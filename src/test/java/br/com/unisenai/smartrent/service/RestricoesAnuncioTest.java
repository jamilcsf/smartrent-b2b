package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Restricoes temporarias (pedido de exclusao de dados em andamento) nos fluxos de anuncio: publicar,
 * republicar (automatico e por confirmacao de edicao) ficam bloqueados; descartar edicao e editar
 * rascunho continuam funcionando. A decisao e do AccountRestrictionService (mock aqui).
 */
@ExtendWith(MockitoExtension.class)
class RestricoesAnuncioTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock private ImovelRepository imovelRepository;
    @Mock private AnuncioRascunhoRepository rascunhoRepository;
    @Mock private AuditoriaService auditoria;
    @Mock private AnuncioGestorMapper mapper;
    @Mock private MidiaService midiaService;
    @Mock private AccountRestrictionService restricoes;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RelogioFalso relogio;
    private AnuncioService anuncioService;
    private AnuncioEdicaoService edicaoService;
    private Usuario gestor;
    private Imovel imovel;
    private AnuncioRascunho rascunhoSalvo;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(T0);
        AnuncioProperties props = new AnuncioProperties(24, 2, 24, 24, 5);
        anuncioService = new AnuncioService(imovelRepository, new ImovelAcesso(imovelRepository), auditoria, mapper,
                props, relogio, midiaService, restricoes);
        edicaoService = new AnuncioEdicaoService(imovelRepository, rascunhoRepository, new ImovelAcesso(imovelRepository),
                auditoria, mapper, midiaService, props, relogio, objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), restricoes);

        gestor = new Usuario();
        gestor.setId(10L);
        gestor.setPapel(PapelUsuario.ANFITRIAO);
        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setUsuario(gestor);
        imovel.setAtivo(true);
        imovel.setValorDiariaBase(new BigDecimal("300.00"));
        AnuncioCampos.aplicar(imovel, new AnuncioDados("Apto Original", "Descricao do imovel", TipoImovel.APARTAMENTO, 70,
                2, 1, 1, 4, List.of("Wi-Fi"), "88054-000", "Rua das Gaivotas", "120", null, "Canasvieiras",
                "Florianopolis", "SC", 1, BigDecimal.ZERO, null));
        lenient().when(imovelRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(imovel));
        lenient().when(imovelRepository.save(any(Imovel.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(rascunhoRepository.save(any(AnuncioRascunho.class))).thenAnswer(i -> {
            rascunhoSalvo = i.getArgument(0);
            return rascunhoSalvo;
        });
        lenient().when(rascunhoRepository.findByImovelId(1L)).thenAnswer(i -> Optional.ofNullable(rascunhoSalvo));
    }

    private void restringir() {
        AcessoNegadoException negado = new AcessoNegadoException("restrito pela solicitacao de exclusao");
        lenient().doThrow(negado).when(restricoes).exigirPodePublicar(gestor);
        lenient().doThrow(negado).when(restricoes).exigirPodeConfirmarEdicao(gestor);
    }

    @Test
    @DisplayName("CT489 - Com pedido de exclusao em andamento, publicar e recusado e o anuncio continua pronto (nada e gravado)")
    void publicarBloqueado() {
        imovel.setStatus(StatusAnuncio.PRONTO_PARA_PUBLICAR);
        restringir();

        assertThrows(AcessoNegadoException.class, () -> anuncioService.publicar(gestor, 1L, true, "ip"));

        assertEquals(StatusAnuncio.PRONTO_PARA_PUBLICAR, imovel.getStatus());
        verify(auditoria, never()).aceite(any(), any(), any(), any());
        verify(imovelRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT490 - Sem pedido em andamento, publicar funciona normalmente")
    void publicarLiberado() {
        imovel.setStatus(StatusAnuncio.PRONTO_PARA_PUBLICAR);

        anuncioService.publicar(gestor, 1L, true, "ip");

        assertEquals(StatusAnuncio.PUBLICADO, imovel.getStatus());
        verify(restricoes).exigirPodePublicar(gestor);
    }

    @Test
    @DisplayName("CT491 - Confirmar alteracao (republicar) e recusado; descartar a edicao e editar o rascunho continuam permitidos")
    void confirmarBloqueadoDescartarLiberado() {
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        edicaoService.iniciar(gestor, 1L, true);
        restringir();

        assertThrows(AcessoNegadoException.class, () -> edicaoService.confirmar(gestor, 1L, true, "ip"));
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus(), "a edicao segue aberta");

        // editar o rascunho nao passa pelas restricoes
        AnuncioDados novo = new AnuncioDados("Apto Novo Titulo", "Descricao do imovel", TipoImovel.APARTAMENTO, 70,
                2, 1, 1, 4, List.of("Wi-Fi"), "88054-000", "Rua das Gaivotas", "120", null, "Canasvieiras",
                "Florianopolis", "SC", 1, BigDecimal.ZERO, null);
        assertDoesNotThrow(() -> edicaoService.salvarRascunho(gestor, 1L, novo));

        // descartar: o anuncio volta ao estado anterior, sem consultar as restricoes
        edicaoService.descartar(gestor, 1L, true);
        assertEquals(StatusAnuncio.PUBLICADO, imovel.getStatus());
        verify(restricoes, never()).exigirPodePublicar(any());
        verify(restricoes, times(1)).exigirPodeConfirmarEdicao(gestor); // so a tentativa de confirmar consultou
    }

    @Test
    @DisplayName("CT492 - Republicacao automatica pula gestor restrito quando as restricoes estao ligadas; desligadas, usa a promocao de sempre")
    void republicacaoAutomatica() {
        when(restricoes.habilitadas()).thenReturn(true);
        when(imovelRepository.promoverRepublicacoesVencidasSemRestritos(T0)).thenReturn(2);
        when(imovelRepository.promoverProntosParaPublicar(T0.minusHours(24))).thenReturn(1);
        assertEquals(3, anuncioService.promoverVencidos());
        verify(imovelRepository, never()).promoverRepublicacoesVencidas(any());

        when(restricoes.habilitadas()).thenReturn(false);
        when(imovelRepository.promoverRepublicacoesVencidas(T0)).thenReturn(4);
        assertEquals(5, anuncioService.promoverVencidos());
    }
}
