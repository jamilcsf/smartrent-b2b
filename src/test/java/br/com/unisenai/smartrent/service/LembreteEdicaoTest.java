package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.LembreteEdicao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.LembreteEdicaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Lembrete de edicao esquecida: quando envia, idempotencia, repeticao, parada e isolamento de falhas. */
@ExtendWith(MockitoExtension.class)
class LembreteEdicaoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private LembreteEdicaoRepository lembreteRepository;
    @Mock
    private NotificacaoService notificacaoService;
    @Mock
    private NotificadorEmail notificadorEmail;

    private RelogioFalso relogio;
    private LembreteEdicaoProcessador processador;
    private Imovel imovel;
    private final List<LembreteEdicao> tabela = new ArrayList<>();

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(T0.plusHours(1)); // a edicao comecou em T0
        processador = novoProcessador(new AnuncioProperties(24, 2, 24, 24, 5));

        Usuario gestor = new Usuario();
        gestor.setId(10L);
        gestor.setEmail("gestor@smartrent.dev");
        gestor.setPapel(PapelUsuario.ANFITRIAO);
        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setUsuario(gestor);
        imovel.setTitulo("Apto Canasvieiras");
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        imovel.setEdicaoIniciadaEm(T0);

        lenient().when(imovelRepository.findById(1L)).thenReturn(Optional.of(imovel));
        lenient().when(notificadorEmail.canal()).thenReturn("EMAIL_LOG");
        // "Banco" em memoria com a chave unica (imovel, edicao, numero, canal).
        lenient().when(lembreteRepository.findByImovelIdAndEdicaoIniciadaEmAndNumeroAndCanal(
                anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(), anyString()))
                .thenAnswer(i -> tabela.stream().filter(l ->
                        l.getImovel().getId().equals(i.getArgument(0))
                                && l.getEdicaoIniciadaEm().equals(i.getArgument(1))
                                && l.getNumero() == (int) i.getArgument(2)
                                && l.getCanal().equals(i.getArgument(3))).findFirst());
        lenient().when(lembreteRepository.saveAndFlush(any(LembreteEdicao.class))).thenAnswer(i -> {
            LembreteEdicao l = i.getArgument(0);
            if (!tabela.contains(l)) {
                tabela.add(l);
            }
            return l;
        });
        lenient().when(lembreteRepository.save(any(LembreteEdicao.class))).thenAnswer(i -> i.getArgument(0));
    }

    private LembreteEdicaoProcessador novoProcessador(AnuncioProperties props) {
        return new LembreteEdicaoProcessador(imovelRepository, lembreteRepository, notificacaoService,
                notificadorEmail, props, relogio, "http://smartrent.test");
    }

    private void passar(Duration d) {
        relogio.avancar(d);
    }

    private long enviados() {
        return tabela.stream().filter(l -> l.getEnviadoEm() != null).count();
    }

    // --------------------------------------------------------- quando envia

    @Test
    @DisplayName("CT140 - Nenhum lembrete antes de 24h em EM_EDICAO")
    void naoEnviaAntesDe24h() {
        passar(Duration.ofHours(22).plusMinutes(58)); // 23h59 desde T0
        processador.processar(1L);

        verifyNoInteractions(notificacaoService);
        verify(notificadorEmail, never()).enviar(any(), any(), any());
        assertTrue(tabela.isEmpty());
    }

    @Test
    @DisplayName("CT141 - Aos 24h envia o lembrete (in-app e e-mail) citando anuncio, tempo, estado e link do dashboard")
    void enviaAos24h() {
        passar(Duration.ofHours(23)); // 24h desde T0
        processador.processar(1L);

        verify(notificacaoService).criar(any(Usuario.class), eq(1L),
                eq("Anúncio fora do ar há 24 horas"),
                org.mockito.ArgumentMatchers.argThat(m -> m.contains("Apto Canasvieiras")
                        && m.contains("24 horas") && m.contains("fora do catálogo")
                        && m.contains("sem receber novas reservas")
                        && m.contains("Confirmar alteração") && m.contains("Descartar edição")),
                eq("/dashboard.html?imovel=1"));
        verify(notificadorEmail).enviar(any(Usuario.class), eq("Anúncio fora do ar há 24 horas"),
                org.mockito.ArgumentMatchers.argThat(c -> c.contains("http://smartrent.test/dashboard.html?imovel=1")));
        assertEquals(2, enviados());
    }

    @Test
    @DisplayName("CT142 - Rodar o job duas vezes (ou atrasado) nao duplica o lembrete")
    void idempotente() {
        passar(Duration.ofHours(23));
        processador.processar(1L);
        processador.processar(1L);
        passar(Duration.ofHours(3));
        processador.processar(1L);

        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any());
        verify(notificadorEmail, times(1)).enviar(any(), any(), any());
        assertEquals(2, tabela.size());
    }

    @Test
    @DisplayName("CT143 - Repete no intervalo configurado: 2o lembrete so 24h depois do 1o")
    void repeteNoIntervalo() {
        passar(Duration.ofHours(23)); // 24h
        processador.processar(1L);
        passar(Duration.ofHours(23)); // 47h: ainda so o primeiro
        processador.processar(1L);
        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any());

        passar(Duration.ofHours(1)); // 48h: segundo lembrete
        processador.processar(1L);

        verify(notificacaoService, times(2)).criar(any(), any(), any(), any(), any());
        assertEquals(2, tabela.stream().map(LembreteEdicao::getNumero).distinct().count());
    }

    @Test
    @DisplayName("CT144 - Intervalo e maximo de lembretes vem de configuracao")
    void intervaloEMaximoConfiguraveis() {
        processador = novoProcessador(new AnuncioProperties(24, 2, 24, 12, 2));
        passar(Duration.ofHours(23));   // 24h  -> #1
        processador.processar(1L);
        passar(Duration.ofHours(12));   // 36h  -> #2
        processador.processar(1L);
        passar(Duration.ofHours(48));   // 84h  -> acima do maximo (2)
        processador.processar(1L);

        verify(notificacaoService, times(2)).criar(any(), any(), any(), any(), any());
        assertTrue(tabela.stream().allMatch(l -> l.getNumero() <= 2));
    }

    @Test
    @DisplayName("CT145 - Job atrasado envia so o lembrete mais recente, nao empilha os perdidos")
    void jobAtrasadoNaoEmpilha() {
        passar(Duration.ofHours(24 * 3)); // 73h desde T0: devido o #3
        processador.processar(1L);

        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any());
        assertEquals(List.of(3), tabela.stream().map(LembreteEdicao::getNumero).distinct().toList());
    }

    // -------------------------------------------------------------- parada

    @Test
    @DisplayName("CT146 - Ao confirmar ou descartar (saiu de EM_EDICAO) os lembretes param")
    void paraQuandoSaiDeEdicao() {
        passar(Duration.ofHours(23));
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA); // confirmou
        processador.processar(1L);
        imovel.setStatus(StatusAnuncio.PUBLICADO);             // descartou
        processador.processar(1L);

        verifyNoInteractions(notificacaoService);
        assertTrue(tabela.isEmpty());
    }

    @Test
    @DisplayName("CT147 - O job de lembrete nao altera estado, dados nem rascunho do imovel")
    void jobSoLe() {
        passar(Duration.ofHours(23 + 24 * 2));
        processador.processar(1L);

        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus());
        assertEquals(T0, imovel.getEdicaoIniciadaEm());
        assertEquals("Apto Canasvieiras", imovel.getTitulo());
        assertNull(imovel.getRepublicarEm());
        verify(imovelRepository, never()).save(any());
        verify(imovelRepository, never()).saveAndFlush(any());
        verify(imovelRepository, never()).delete(any());
    }

    // -------------------------------------------------------------- falhas

    @Test
    @DisplayName("CT148 - Falha ao notificar fica registrada, nao afeta o imovel e e retentada na proxima execucao")
    void falhaEhRegistradaERetentada() {
        passar(Duration.ofHours(23));
        doThrow(new IllegalStateException("caixa postal cheia"))
                .when(notificadorEmail).enviar(any(), any(), any());

        processador.processar(1L); // e-mail falha; in-app entrega

        LembreteEdicao email = tabela.stream().filter(l -> l.getCanal().equals("EMAIL_LOG")).findFirst().orElseThrow();
        assertNull(email.getEnviadoEm());
        assertTrue(email.getErro().contains("caixa postal cheia"));
        assertEquals(1, email.getTentativas());
        assertEquals(StatusAnuncio.EM_EDICAO, imovel.getStatus(), "imovel intacto");
        verify(imovelRepository, never()).save(any());

        doNothing().when(notificadorEmail).enviar(any(), any(), any());
        passar(Duration.ofMinutes(10));
        processador.processar(1L); // proxima execucao: so o e-mail e tentado de novo

        assertNotNull(email.getEnviadoEm());
        assertNull(email.getErro());
        assertEquals(2, email.getTentativas());
        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CT149 - Falha em um imovel nao trava o job: os demais sao processados")
    void falhaNaoTravaOJob() {
        Imovel outro = new Imovel();
        outro.setId(2L);
        outro.setStatus(StatusAnuncio.EM_EDICAO);
        when(imovelRepository.findByStatus(StatusAnuncio.EM_EDICAO)).thenReturn(List.of(imovel, outro));
        LembreteEdicaoProcessador processadorFalso = mock(LembreteEdicaoProcessador.class);
        doThrow(new RuntimeException("banco indisponivel")).when(processadorFalso).processar(1L);

        new LembreteEdicaoJob(imovelRepository, processadorFalso).executar();

        verify(processadorFalso).processar(1L);
        verify(processadorFalso).processar(2L);
    }

    @Test
    @DisplayName("CT150 - Texto do tempo: horas ate 47h, dias a partir de 2 dias")
    void descricaoDoTempo() {
        assertEquals("24 horas", LembreteEdicaoProcessador.descrever(Duration.ofHours(24)));
        assertEquals("47 horas", LembreteEdicaoProcessador.descrever(Duration.ofHours(47)));
        assertEquals("2 dias", LembreteEdicaoProcessador.descrever(Duration.ofHours(50)));
    }
}
