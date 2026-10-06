package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Edicao de anuncio publicado: sai do ar ao iniciar, relogio de 2h so na
 * confirmacao, descarte imediato e sem suspensao, rascunho isolado do anuncio vivo.
 */
@ExtendWith(MockitoExtension.class)
class AnuncioEdicaoServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private AnuncioRascunhoRepository rascunhoRepository;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private AnuncioGestorMapper mapper;
    @Mock
    private MidiaService midiaService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RelogioFalso relogio;
    private AnuncioEdicaoService service;
    private Usuario gestor;
    private Imovel imovel;
    private AnuncioRascunho rascunhoSalvo;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(T0);
        service = new AnuncioEdicaoService(imovelRepository, rascunhoRepository,
                new ImovelAcesso(imovelRepository), auditoria, mapper, midiaService,
                new AnuncioProperties(24, 2, 24, 24, 5), relogio, objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                org.mockito.Mockito.mock(AccountRestrictionService.class));

        gestor = new Usuario();
        gestor.setId(10L);
        gestor.setPapel(PapelUsuario.ANFITRIAO);

        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setUsuario(gestor);
        imovel.setAtivo(true);
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        imovel.setValorDiariaBase(new BigDecimal("300.00"));
        AnuncioCampos.aplicar(imovel, dadosValidos("Apto Original"));
        lenient().when(imovelRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(imovel));
        lenient().when(imovelRepository.save(any(Imovel.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(rascunhoRepository.save(any(AnuncioRascunho.class))).thenAnswer(i -> {
            rascunhoSalvo = i.getArgument(0);
            return rascunhoSalvo;
        });
        lenient().when(rascunhoRepository.findByImovelId(1L)).thenAnswer(i -> Optional.ofNullable(rascunhoSalvo));
    }

    private static AnuncioDados dadosValidos(String titulo) {
        return new AnuncioDados(titulo, "Descricao do imovel", TipoImovel.APARTAMENTO, 70, 2, 1, 1, 4,
                List.of("Wi-Fi"), "88054-000", "Rua das Gaivotas", "120", null, "Canasvieiras",
                "Florianopolis", "SC", 1, BigDecimal.ZERO, null);
    }

    private AnuncioDados rascunhoAtual() throws Exception {
        return objectMapper.readValue(rascunhoSalvo.getDados(), AnuncioDados.class);
    }

    private void iniciar() {
        service.iniciar(gestor, 1L, true);
    }

    // ----------------------------------------------------------------- iniciar

    @Test
    @DisplayName("CT110 - Sem o 'sim' do modal, a edicao nao inicia e o anuncio continua no ar")
    void semConfirmacaoNadaMuda() {
        assertThrows(ValidacaoAnuncioException.class, () -> service.iniciar(gestor, 1L, false));
        assertThrows(ValidacaoAnuncioException.class, () -> service.iniciar(gestor, 1L, null));

        assertEquals(StatusAnuncio.PUBLICADO, imovel.getStatus());
        assertTrue(AnuncioGestorMapper.noCatalogo(imovel, relogio.agora()));
        verify(imovelRepository, never()).save(any());
        verify(rascunhoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT111 - Iniciar tira o anuncio do catalogo na hora e guarda origem e instante de inicio")
    void iniciarRemoveDoCatalogoImediatamente() {
        iniciar();

        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        assertFalse(AnuncioGestorMapper.noCatalogo(imovel, relogio.agora()), "fora do catalogo");
        assertEquals(T0, imovel.getEdicaoIniciadaEm());
        assertEquals(StatusAnuncio.PUBLICADO, imovel.getEdicaoEstadoOrigem());
        assertNull(imovel.getRepublicarOriginalEm());
        assertNotNull(rascunhoSalvo, "rascunho criado a partir dos dados atuais");
        verify(auditoria).acao(eq(imovel), eq(10L), eq("EDICAO_INICIADA"), any());
    }

    @Test
    @DisplayName("CT112 - Anuncio em pre-publicacao nao entra no fluxo de edicao (e editado direto)")
    void prePublicacaoNaoUsaFluxoDeEdicao() {
        imovel.setStatus(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO);
        assertThrows(TransicaoInvalidaException.class, () -> service.iniciar(gestor, 1L, true));
    }

    @Test
    @DisplayName("CT113 - Nao se inicia edicao de quem ja esta em edicao")
    void naoReiniciaEdicao() {
        iniciar();
        assertThrows(TransicaoInvalidaException.class, () -> service.iniciar(gestor, 1L, true));
    }

    @Test
    @DisplayName("CT114 - Iniciar edicao em REPUBLICACAO_AGENDADA cancela a republicacao e guarda o horario original")
    void iniciarEmAgendadoCancelaRepublicacao() {
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovel.setRepublicarEm(T0.plusMinutes(90));

        iniciar();

        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        assertEquals(StatusAnuncio.REPUBLICACAO_AGENDADA, imovel.getEdicaoEstadoOrigem());
        assertEquals(T0.plusMinutes(90), imovel.getRepublicarOriginalEm());
        assertNull(imovel.getRepublicarEm(), "agendamento suspenso");
        // Mesmo depois do horario antigo, nada o republica: esta em edicao.
        relogio.avancar(Duration.ofHours(5));
        assertFalse(AnuncioGestorMapper.noCatalogo(imovel, relogio.agora()));
    }

    @Test
    @DisplayName("CT115 - Republicacao vencida cujo job atrasou conta como PUBLICADO ao iniciar a edicao")
    void agendadoVencidoViraPublicadoAoIniciar() {
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovel.setRepublicarEm(T0.minusMinutes(1));

        iniciar();

        assertEquals(StatusAnuncio.PUBLICADO, imovel.getEdicaoEstadoOrigem());
        assertNull(imovel.getRepublicarOriginalEm());
    }

    @Test
    @DisplayName("CT116 - Gestor de outro imovel nao inicia, salva, confirma nem descarta edicao")
    void edicaoDeImovelAlheio() {
        imovel.setUsuario(outroGestor());
        assertThrows(AcessoNegadoException.class, () -> service.iniciar(gestor, 1L, true));
        assertThrows(AcessoNegadoException.class, () -> service.salvarRascunho(gestor, 1L, dadosValidos("x")));
        assertThrows(AcessoNegadoException.class, () -> service.confirmar(gestor, 1L, true, "ip"));
        assertThrows(AcessoNegadoException.class, () -> service.descartar(gestor, 1L, true));
    }

    private Usuario outroGestor() {
        Usuario u = new Usuario();
        u.setId(99L);
        u.setPapel(PapelUsuario.ANFITRIAO);
        return u;
    }

    // ---------------------------------------------------------------- rascunho

    @Test
    @DisplayName("CT117 - O rascunho nao altera o anuncio vivo: so a confirmacao aplica")
    void rascunhoNaoAfetaAnuncioVivo() throws Exception {
        iniciar();

        service.salvarRascunho(gestor, 1L, dadosValidos("Titulo Novo"));

        assertEquals("Titulo Novo", rascunhoAtual().titulo());
        assertEquals("Apto Original", imovel.getTitulo(), "anuncio vivo intacto");
        assertEquals(new BigDecimal("300.00"), imovel.getValorDiariaBase());
    }

    @Test
    @DisplayName("CT118 - O rascunho e sanitizado e salvo so durante a edicao")
    void rascunhoSanitizadoESoEmEdicao() throws Exception {
        assertThrows(TransicaoInvalidaException.class,
                () -> service.salvarRascunho(gestor, 1L, dadosValidos("x")));

        iniciar();
        service.salvarRascunho(gestor, 1L, dadosValidos("<script>x</script>Casa"));

        assertEquals("Casa", rascunhoAtual().titulo()); // o conteudo do script some junto com a tag
    }

    @Test
    @DisplayName("CT119 - O imovel fica em EM_EDICAO indefinidamente: tempo passando nao muda nada")
    void semPrazoMaximoDeEdicao() {
        iniciar();
        relogio.avancar(Duration.ofDays(90));

        // Nada no servico de edicao reage ao tempo; so as acoes do gestor mudam o estado.
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        assertFalse(AnuncioGestorMapper.noCatalogo(imovel, relogio.agora()));
        assertNull(imovel.getRepublicarEm());
        verify(rascunhoRepository, never()).delete(any());
        verify(rascunhoRepository, never()).deleteByImovelId(any());
    }

    // --------------------------------------------------------------- confirmar

    @Test
    @DisplayName("CT120 - Confirmar exige o aceite do termo de uso")
    void confirmarExigeAceite() {
        iniciar();
        assertThrows(ValidacaoAnuncioException.class, () -> service.confirmar(gestor, 1L, false, "ip"));
        assertThrows(ValidacaoAnuncioException.class, () -> service.confirmar(gestor, 1L, null, "ip"));
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
    }

    @Test
    @DisplayName("CT121 - O relogio das 2h comeca SO na confirmacao: o tempo editando nao conta")
    void relogioDe2hComecaNaConfirmacao() {
        iniciar();
        relogio.avancar(Duration.ofHours(30)); // editou por 30h

        LocalDateTime confirmacao = relogio.agora();
        service.confirmar(gestor, 1L, true, "10.0.0.1");

        assertEquals(StatusAnuncio.REPUBLICACAO_AGENDADA, imovel.getStatus());
        assertEquals(confirmacao, imovel.getEdicaoConfirmadaEm());
        assertEquals(confirmacao.plusHours(2), imovel.getRepublicarEm());
        assertNull(imovel.getEdicaoIniciadaEm());
        assertFalse(AnuncioGestorMapper.noCatalogo(imovel, confirmacao.plusMinutes(119)), "ainda fora do ar");
        assertTrue(AnuncioGestorMapper.noCatalogo(imovel, confirmacao.plusHours(2)), "volta exatamente em 2h");
    }

    @Test
    @DisplayName("CT122 - Confirmar aplica o rascunho, apaga o rascunho, efetiva as midias e audita aceite e preco")
    void confirmarAplicaRascunho() throws Exception {
        iniciar();
        AnuncioDados novo = new AnuncioDados("Titulo Novo", "Outra descricao", TipoImovel.CASA, 90, 3, 2, 2, 6,
                List.of("Piscina"), "88054-000", "Rua Nova", "5", null, "Centro", "Florianopolis", "SC", 2, new BigDecimal("60.00"),
                new BigDecimal("450"));
        service.salvarRascunho(gestor, 1L, novo);

        service.confirmar(gestor, 1L, true, "10.0.0.7");

        assertEquals("Titulo Novo", imovel.getTitulo());
        assertEquals(TipoImovel.CASA, imovel.getTipoImovel());
        assertEquals(new BigDecimal("450.00"), imovel.getValorDiariaBase());
        verify(midiaService).aplicarRascunho(eq(imovel), any(AnuncioRascunho.class));
        verify(rascunhoRepository).delete(any(AnuncioRascunho.class));
        verify(auditoria).aceite(gestor, imovel, ContextoAceite.CONFIRMACAO_EDICAO, "10.0.0.7");
        verify(auditoria).preco(eq(imovel), eq(new BigDecimal("300.00")), eq(new BigDecimal("450.00")),
                eq(gestor), eq(OrigemPreco.MANUAL));
    }

    @Test
    @DisplayName("CT123 - Rascunho invalido nao pode ser confirmado e o anuncio segue em edicao")
    void confirmarRascunhoInvalido() {
        iniciar();
        AnuncioDados invalido = new AnuncioDados("", null, TipoImovel.CASA, -3, 1, 1, null, 2, List.of(),
                "000", "Rua", "1", null, "Centro", "Floripa", "SC", null, null, null);
        rascunhoSalvo.setDados(serializar(invalido));

        var erro = assertThrows(ValidacaoAnuncioException.class, () -> service.confirmar(gestor, 1L, true, "ip"));

        assertTrue(erro.getCampos().containsKey("titulo"));
        assertTrue(erro.getCampos().containsKey("metragemQuadrada"));
        assertTrue(erro.getCampos().containsKey("cep"));
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        assertEquals("Apto Original", imovel.getTitulo());
        verify(midiaService, never()).aplicarRascunho(any(), any());
    }

    private String serializar(AnuncioDados d) {
        try {
            return objectMapper.writeValueAsString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("CT124 - Nao se confirma duas vezes: depois de confirmar o imovel ja esta em REPUBLICACAO_AGENDADA")
    void naoConfirmaDuasVezes() {
        iniciar();
        service.confirmar(gestor, 1L, true, "ip");
        assertThrows(TransicaoInvalidaException.class, () -> service.confirmar(gestor, 1L, true, "ip"));
    }

    // ---------------------------------------------------------------- descartar

    @Test
    @DisplayName("CT125 - Descartar exige confirmacao explicita (apaga o rascunho)")
    void descartarExigeConfirmacao() {
        iniciar();
        assertThrows(ValidacaoAnuncioException.class, () -> service.descartar(gestor, 1L, false));
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        verify(rascunhoRepository, never()).deleteByImovelId(any());
    }

    @Test
    @DisplayName("CT126 - Descartar devolve o anuncio ao ar NA HORA, sem suspensao de 2h, com dados originais intactos")
    void descartarVoltaImediatamente() {
        iniciar();
        service.salvarRascunho(gestor, 1L, dadosValidos("Titulo que sera perdido"));
        relogio.avancar(Duration.ofHours(7));

        service.descartar(gestor, 1L, true);

        assertEquals(StatusAnuncio.PUBLICADO, imovel.getStatus());
        assertTrue(AnuncioGestorMapper.noCatalogo(imovel, relogio.agora()), "visivel imediatamente");
        assertNull(imovel.getRepublicarEm(), "nenhuma suspensao de 2h");
        assertNull(imovel.getEdicaoConfirmadaEm());
        assertNull(imovel.getEdicaoIniciadaEm());
        assertEquals("Apto Original", imovel.getTitulo());
        assertEquals(new BigDecimal("300.00"), imovel.getValorDiariaBase());
    }

    @Test
    @DisplayName("CT127 - Descartar apaga o rascunho, desfaz as midias, nao pede aceite e registra auditoria")
    void descartarLimpaERegistra() {
        iniciar();

        service.descartar(gestor, 1L, true);

        verify(rascunhoRepository).deleteByImovelId(1L);
        verify(midiaService).desfazerRascunho(imovel);
        verify(auditoria, never()).aceite(any(), any(), any(), any());
        verify(auditoria).acao(eq(imovel), eq(10L), eq("EDICAO_DESCARTADA"), any());
    }

    @Test
    @DisplayName("CT128 - Descartar edicao iniciada em REPUBLICACAO_AGENDADA restaura o horario original")
    void descartarRestauraAgendamentoOriginal() {
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovel.setRepublicarEm(T0.plusMinutes(90));
        iniciar();
        relogio.avancar(Duration.ofMinutes(30)); // ainda antes do horario original

        service.descartar(gestor, 1L, true);

        assertEquals(StatusAnuncio.REPUBLICACAO_AGENDADA, imovel.getStatus());
        assertEquals(T0.plusMinutes(90), imovel.getRepublicarEm());
        assertNull(imovel.getRepublicarOriginalEm());
        assertNull(imovel.getEdicaoEstadoOrigem());
    }

    @Test
    @DisplayName("CT129 - Se o horario original ja passou, o descarte vai direto para PUBLICADO")
    void descartarComAgendamentoVencidoVaiParaPublicado() {
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovel.setRepublicarEm(T0.plusMinutes(90));
        iniciar();
        relogio.avancar(Duration.ofHours(5));

        service.descartar(gestor, 1L, true);

        assertEquals(StatusAnuncio.PUBLICADO, imovel.getStatus());
        assertNull(imovel.getRepublicarEm());
    }

    @Test
    @DisplayName("CT130 - Descartar depois de confirmar e bloqueado (o fluxo agora e o da republicacao)")
    void descarteBloqueadoAposConfirmar() {
        iniciar();
        service.confirmar(gestor, 1L, true, "ip");

        var erro = assertThrows(TransicaoInvalidaException.class, () -> service.descartar(gestor, 1L, true));

        assertTrue(erro.getMessage().contains("confirmadas"));
        assertEquals(StatusAnuncio.REPUBLICACAO_AGENDADA, imovel.getStatus());
    }

    @Test
    @DisplayName("CT131 - Descartar so e possivel em EM_EDICAO")
    void descartarForaDeEdicao() {
        assertThrows(TransicaoInvalidaException.class, () -> service.descartar(gestor, 1L, true));
    }

    @Test
    @DisplayName("CT132 - A duracao da suspensao pos-confirmacao vem de configuracao")
    void duracaoConfiguravel() {
        AnuncioEdicaoService quatroHoras = new AnuncioEdicaoService(imovelRepository, rascunhoRepository,
                new ImovelAcesso(imovelRepository), auditoria, mapper, midiaService,
                new AnuncioProperties(24, 4, 24, 24, 5), relogio, objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                org.mockito.Mockito.mock(AccountRestrictionService.class));
        quatroHoras.iniciar(gestor, 1L, true);

        quatroHoras.confirmar(gestor, 1L, true, "ip");

        assertEquals(T0.plusHours(4), imovel.getRepublicarEm());
    }
}
