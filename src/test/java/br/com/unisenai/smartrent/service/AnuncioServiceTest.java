package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.CriarAnuncioRequest;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
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

/** Cadastro, preco, janela de 24h e publicacao, com relogio controlado. */
@ExtendWith(MockitoExtension.class)
class AnuncioServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private AnuncioGestorMapper mapper;

    private RelogioFalso relogio;
    private AnuncioService service;
    private Usuario gestor;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(T0);
        service = new AnuncioService(imovelRepository, new ImovelAcesso(imovelRepository), auditoria, mapper,
                new AnuncioProperties(24, 2, 24, 24, 5), relogio);
        gestor = usuario(10L, PapelUsuario.ANFITRIAO);
        lenient().when(imovelRepository.save(any(Imovel.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Usuario usuario(Long id, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Usuario " + id);
        u.setEmail("u" + id + "@smartrent.dev");
        u.setPapel(papel);
        return u;
    }

    private static AnuncioDados dados() {
        return new AnuncioDados("Apto <b>vista</b> mar", "Descricao <script>alert(1)</script>boa",
                TipoImovel.APARTAMENTO, 70, 2, 1, 1, 4, List.of("Wi-Fi", "Wi-Fi", " Piscina "),
                "88054-000", "Rua das Gaivotas", "120", null, "Canasvieiras", "Florianopolis", "sc",
                1, new java.math.BigDecimal("80.00"), null);
    }

    private Imovel imovelEm(StatusAnuncio status) {
        Imovel i = new Imovel();
        i.setId(1L);
        i.setUsuario(gestor);
        i.setStatus(status);
        i.setAtivo(true);
        when(imovelRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(i));
        return i;
    }

    // ------------------------------------------------------------ cadastro

    @Test
    @DisplayName("CT50 - Cadastro nasce em PRE_PUBLICACAO_SEM_PRECO, sem preco, com texto sanitizado e aceite auditado")
    void cadastroNasceSemPreco() {
        service.criar(gestor, new CriarAnuncioRequest(dados(), true), "10.0.0.1");

        ArgumentCaptor<Imovel> capturado = ArgumentCaptor.forClass(Imovel.class);
        verify(imovelRepository).save(capturado.capture());
        Imovel salvo = capturado.getValue();
        assertEquals(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO, salvo.getStatus());
        assertNull(salvo.getValorDiariaBase());
        assertNull(salvo.getPrecoPrimeiraConfirmacaoEm());
        assertEquals("Apto vista mar", salvo.getTitulo());
        assertFalse(salvo.getDescricao().contains("<"), "tags removidas da descricao");
        assertEquals(List.of("Wi-Fi", "Piscina"), salvo.getComodidades(), "duplicadas e espacos tratados");
        assertEquals("SC", salvo.getEndereco().getEstado());
        verify(auditoria).aceite(eq(gestor), any(Imovel.class), eq(ContextoAceite.CADASTRO), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("CT51 - Cadastro sem aceite do termo e recusado e nada e salvo")
    void cadastroExigeAceite() {
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.criar(gestor, new CriarAnuncioRequest(dados(), false), "ip"));
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.criar(gestor, new CriarAnuncioRequest(dados(), null), "ip"));
        verify(imovelRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT52 - Usuario comum (CLIENTE) nao pode cadastrar anuncio")
    void clienteNaoCadastra() {
        Usuario cliente = usuario(20L, PapelUsuario.CLIENTE);
        assertThrows(AcessoNegadoException.class,
                () -> service.criar(cliente, new CriarAnuncioRequest(dados(), true), "ip"));
        verify(imovelRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT53 - Gestor nao enxerga nem altera imovel de outro gestor")
    void gestorSoMexeNosProprios() {
        Imovel alheio = imovelEm(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        alheio.setUsuario(usuario(99L, PapelUsuario.ANFITRIAO));

        assertThrows(AcessoNegadoException.class,
                () -> service.definirPreco(gestor, 1L, new BigDecimal("300"), OrigemPreco.MANUAL));
        assertThrows(AcessoNegadoException.class, () -> service.publicar(gestor, 1L, true, "ip"));
        assertThrows(AcessoNegadoException.class, () -> service.atualizar(gestor, 1L, dados()));
    }

    // --------------------------------------------------------- preco e janela

    @Test
    @DisplayName("CT54 - Primeira confirmacao de preco leva a AGUARDANDO e grava o instante inicial")
    void primeiroPrecoIniciaJanela() {
        Imovel i = imovelEm(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);

        service.definirPreco(gestor, 1L, new BigDecimal("350.5"), OrigemPreco.MANUAL);

        assertEquals(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, i.getStatus());
        assertEquals(T0, i.getPrecoPrimeiraConfirmacaoEm());
        assertEquals(new BigDecimal("350.50"), i.getValorDiariaBase());
        verify(auditoria).preco(eq(i), isNull(), eq(new BigDecimal("350.50")), eq(gestor), eq(OrigemPreco.MANUAL));
    }

    @Test
    @DisplayName("CT55 - O relogio das 24h NAO reinicia quando o preco muda (manual ou IA)")
    void alterarPrecoNaoReiniciaRelogio() {
        Imovel i = imovelEm(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        service.definirPreco(gestor, 1L, new BigDecimal("300"), OrigemPreco.MANUAL);

        relogio.avancar(Duration.ofHours(10));
        service.definirPreco(gestor, 1L, new BigDecimal("320"), OrigemPreco.IA);
        relogio.avancar(Duration.ofHours(5));
        service.definirPreco(gestor, 1L, new BigDecimal("310"), OrigemPreco.MANUAL);

        assertEquals(T0, i.getPrecoPrimeiraConfirmacaoEm(), "instante da primeira confirmacao preservado");
        assertEquals(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, i.getStatus());
        assertEquals(new BigDecimal("310.00"), i.getValorDiariaBase());
    }

    @Test
    @DisplayName("CT56 - Mesmo valor reenviado nao gera historico nem mexe no relogio")
    void mesmoValorNaoFazNada() {
        imovelEm(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        service.definirPreco(gestor, 1L, new BigDecimal("300"), OrigemPreco.MANUAL);

        service.definirPreco(gestor, 1L, new BigDecimal("300.00"), OrigemPreco.MANUAL);

        verify(auditoria, times(1)).preco(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CT57 - Preco fora da faixa e recusado")
    void precoInvalido() {
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.definirPreco(gestor, 1L, new BigDecimal("0.50"), OrigemPreco.MANUAL));
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.definirPreco(gestor, 1L, null, OrigemPreco.MANUAL));
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.definirPreco(gestor, 1L, new BigDecimal("999999"), OrigemPreco.MANUAL));
    }

    @Test
    @DisplayName("CT58 - Preco de anuncio publicado so muda pelo fluxo de edicao")
    void precoDePublicadoBloqueado() {
        imovelEm(StatusAnuncio.PUBLICADO);
        assertThrows(TransicaoInvalidaException.class,
                () -> service.definirPreco(gestor, 1L, new BigDecimal("500"), OrigemPreco.MANUAL));
    }

    @Test
    @DisplayName("CT59 - Preco ainda pode ser ajustado em PRONTO_PARA_PUBLICAR, sem trocar o status")
    void precoAjustavelEmPronto() {
        Imovel i = imovelEm(StatusAnuncio.PRONTO_PARA_PUBLICAR);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setPrecoPrimeiraConfirmacaoEm(T0.minusHours(30));

        service.definirPreco(gestor, 1L, new BigDecimal("280"), OrigemPreco.MANUAL);

        assertEquals(StatusAnuncio.PRONTO_PARA_PUBLICAR, i.getStatus());
        assertEquals(T0.minusHours(30), i.getPrecoPrimeiraConfirmacaoEm());
        assertEquals(new BigDecimal("280.00"), i.getValorDiariaBase());
    }

    // ------------------------------------------------------------- publicacao

    private Imovel aguardandoDesde(LocalDateTime inicio) {
        Imovel i = imovelEm(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setPrecoPrimeiraConfirmacaoEm(inicio);
        return i;
    }

    @Test
    @DisplayName("CT60 - Publicar antes de 24h da primeira confirmacao e bloqueado pelo servidor")
    void publicarAntesDe24hBloqueado() {
        Imovel i = aguardandoDesde(T0.minusHours(23).minusMinutes(59));

        assertThrows(TransicaoInvalidaException.class, () -> service.publicar(gestor, 1L, true, "ip"));

        assertEquals(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, i.getStatus());
        verify(auditoria, never()).aceite(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CT61 - Passadas as 24h, publica mesmo que o job nao tenha rodado (status ainda AGUARDANDO)")
    void publicarAposJanelaMesmoSemJob() {
        Imovel i = aguardandoDesde(T0.minusHours(24));

        service.publicar(gestor, 1L, true, "10.0.0.9");

        assertEquals(StatusAnuncio.PUBLICADO, i.getStatus());
        assertEquals(T0, i.getPublicadoEm());
        verify(auditoria).aceite(eq(gestor), eq(i), eq(ContextoAceite.PUBLICACAO), eq("10.0.0.9"));
    }

    @Test
    @DisplayName("CT62 - Publicar exige novo aceite do termo")
    void publicarExigeAceite() {
        Imovel i = aguardandoDesde(T0.minusHours(25));

        assertThrows(ValidacaoAnuncioException.class, () -> service.publicar(gestor, 1L, false, "ip"));
        assertThrows(ValidacaoAnuncioException.class, () -> service.publicar(gestor, 1L, null, "ip"));

        assertNotEquals(StatusAnuncio.PUBLICADO, i.getStatus());
    }

    @Test
    @DisplayName("CT63 - Sem preco definido nao ha publicacao")
    void publicarSemPreco() {
        imovelEm(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        assertThrows(TransicaoInvalidaException.class, () -> service.publicar(gestor, 1L, true, "ip"));
    }

    @Test
    @DisplayName("CT64 - A duracao da janela vem de configuracao")
    void janelaConfiguravel() {
        AnuncioService curto = new AnuncioService(imovelRepository, new ImovelAcesso(imovelRepository), auditoria,
                mapper, new AnuncioProperties(1, 2, 24, 24, 5), relogio);
        Imovel i = aguardandoDesde(T0.minusMinutes(61));

        curto.publicar(gestor, 1L, true, "ip");

        assertEquals(StatusAnuncio.PUBLICADO, i.getStatus());
    }

    @Test
    @DisplayName("CT65 - Promocao em lote usa o relogio e a janela configurada, e e repetivel")
    void promocaoEmLote() {
        when(imovelRepository.promoverRepublicacoesVencidas(T0)).thenReturn(2);
        when(imovelRepository.promoverProntosParaPublicar(T0.minusHours(24))).thenReturn(1);

        assertEquals(3, service.promoverVencidos());
        assertEquals(3, service.promoverVencidos());
    }

    @Test
    @DisplayName("CT66 - Edicao direta so em pre-publicacao; publicado exige o fluxo de edicao")
    void edicaoDiretaSoEmPrePublicacao() {
        imovelEm(StatusAnuncio.PUBLICADO);
        assertThrows(TransicaoInvalidaException.class, () -> service.atualizar(gestor, 1L, dados()));
    }
}
