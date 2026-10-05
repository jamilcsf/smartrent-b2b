package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.dto.ExclusaoDadosDtos.Status;
import br.com.unisenai.smartrent.model.AuditoriaConta;
import br.com.unisenai.smartrent.model.DenunciaChat;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.SmartChatConversa;
import br.com.unisenai.smartrent.model.SolicitacaoExclusao;
import br.com.unisenai.smartrent.model.SolicitacaoExclusaoHistorico;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoHistoricoRepository;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Solicitacao de exclusao de dados com banco de verdade (H2): fluxo do usuario, antifraude (senha,
 * e-mail com link "nao fui eu", periodo minimo, sinais de risco), revisao e trava de aprovacao.
 * Em todos os casos NENHUM dado e excluido.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:exclusao;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({PerfilService.class, NomeExibicaoValidador.class, MessageFilterService.class, AuditoriaContaService.class,
        LimitadorDeTaxa.class, ReautenticacaoService.class, ExclusaoDadosService.class, DataDeletionReviewService.class,
        AccountRestrictionService.class, SinaisDeRisco.class, TextosPoliticas.class, ExclusaoDadosIntegracaoTest.Config.class})
class ExclusaoDadosIntegracaoTest {

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0));
        }

        @Bean
        ChatProperties chatProperties() {
            return ChatProperties.padrao();
        }

        @Bean
        br.com.unisenai.smartrent.security.CifraCampo cifraCampo() {
            return new br.com.unisenai.smartrent.security.CifraCampo(new byte[32]);
        }

        @Bean
        PerfilProperties perfilProperties() {
            return PerfilProperties.padrao();
        }

        @Bean
        ExclusaoDadosProperties exclusaoProps() {
            return new ExclusaoDadosProperties(48, 30, "equipe@smartrent.dev", true, 3);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    private static final String SENHA = "Cavalo-Azul-Mesa-77";
    private static final Pattern LINK = Pattern.compile("cancelar-exclusao\\.html\\?token=([A-Za-z0-9_\\-]+)");

    @Autowired private TestEntityManager em;
    @Autowired private ExclusaoDadosService service;
    @Autowired private DataDeletionReviewService revisao;
    @Autowired private AccountRestrictionService restricoes;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RelogioFalso relogio;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SolicitacaoExclusaoRepository solicitacaoRepository;
    @Autowired private SolicitacaoExclusaoHistoricoRepository historicoRepository;
    @Autowired private AuditoriaContaRepository auditoriaRepository;
    @MockBean private EmailSender emailSender;
    @MockBean private GoogleTokenVerifier googleVerifier;
    @MockBean private DataDeletionExecutor executorFalso; // so para o teste de conclusao com executor

    private Usuario ana;
    private Usuario gestor;

    private Usuario conta(String nome, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash(encoder.encode(SENHA));
        u.setPapel(papel);
        u.setAtivo(true);
        em.persist(u);
        em.flush();
        return u;
    }

    private void confirmarTransacao() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    @BeforeEach
    void preparar() {
        ana = conta("Ana Moraes", PapelUsuario.CLIENTE);
        gestor = conta("Gil Moraes", PapelUsuario.ANFITRIAO);
    }

    private Status pedir(Usuario u, String ip) {
        return service.solicitar(u, "quero sair da plataforma", SENHA, null, ip);
    }

    private SolicitacaoExclusao aberta(Usuario u) {
        return solicitacaoRepository.findFirstByUsuarioIdAndEstadoIn(u.getId(), EstadoExclusao.estadosEmAndamento()).orElseThrow();
    }

    private Imovel imovelDo(Usuario dono) {
        Imovel imovel = new Imovel();
        imovel.setUsuario(dono);
        imovel.setTitulo("Apto");
        imovel.setTipoImovel(TipoImovel.APARTAMENTO);
        imovel.setCapacidadeHospedes(2);
        imovel.setNumeroQuartos(1);
        imovel.setNumeroBanheiros(1);
        imovel.setValorDiariaBase(new BigDecimal("100.00"));
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Floripa");
        e.setEstado("SC");
        e.setCep("88000-000");
        imovel.setEndereco(e);
        return em.persist(imovel);
    }

    private Reserva reservaDe(Imovel imovel, Usuario cliente, LocalDate checkin, LocalDate checkout, StatusReserva status) {
        Reserva r = new Reserva();
        r.setImovel(imovel);
        r.setCliente(cliente);
        r.setHospedeNome(cliente.getNome());
        r.setHospedeEmail(cliente.getEmail());
        r.setDataCheckin(checkin);
        r.setDataCheckout(checkout);
        r.setValorTotal(new BigDecimal("200.00"));
        r.setNumeroDiarias(2);
        r.setPrecoDiariaSnapshot(new BigDecimal("100.00"));
        r.setTotalSnapshot(new BigDecimal("200.00"));
        r.setImovelTituloSnapshot("Apto");
        r.setStatus(status);
        r.setDataCriacao(LocalDateTime.of(2026, 10, 1, 12, 0));
        em.persist(r);
        em.flush();
        return r;
    }

    // ----------------------------------------------------------------- pedido

    @Test
    @DisplayName("CT480 - Pedido: cria PENDENTE, historico, auditoria com IP, e-mail ao usuario (com link) e a equipe (sem dados pessoais); NADA e excluido")
    void pedido() {
        Status status = pedir(ana, "10.0.0.5");
        confirmarTransacao();

        assertEquals("PENDENTE", status.solicitacao().estado());
        assertTrue(status.solicitacao().podeCancelar());
        assertEquals(48, status.analiseMinHoras());
        assertFalse(status.acoesRestritas().isEmpty());
        assertEquals(List.of("NOVA_RESERVA", "ALTERAR_EMAIL"), status.restricoesAtivas());

        SolicitacaoExclusao s = aberta(ana);
        assertEquals(EstadoExclusao.PENDENTE, s.getEstado());
        assertEquals(ana.getEmail(), s.getEmailNoPedido());
        assertEquals("quero sair da plataforma", s.getMotivo());
        assertEquals(Duration.ofHours(48), Duration.between(s.getCriadaEm(), s.getAnaliseApos()));
        assertEquals(64, s.getTokenCancelamentoHash().length());
        assertEquals(1, historicoRepository.findBySolicitacaoIdOrderByIdAsc(s.getId()).size());

        List<String> acoes = auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).stream().map(AuditoriaConta::getAcao).toList();
        assertTrue(acoes.contains(AuditoriaContaService.EXCLUSAO_SOLICITADA));
        assertTrue(acoes.contains(AuditoriaContaService.RESTRICOES_INICIO));
        assertEquals("10.0.0.5", auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).get(0).getIp());

        ArgumentCaptor<String> corpoUsuario = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSender).enviar(eq(ana.getEmail()), Mockito.contains("exclusão"), corpoUsuario.capture());
        assertTrue(LINK.matcher(corpoUsuario.getValue()).find(), "o e-mail traz o link Nao fui eu");
        assertTrue(corpoUsuario.getValue().contains("Criar novas reservas"), "lista as acoes temporariamente restritas");

        ArgumentCaptor<String> corpoEquipe = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSender).enviar(eq("equipe@smartrent.dev"), Mockito.contains("#" + s.getId()), corpoEquipe.capture());
        assertTrue(corpoEquipe.getValue().contains("ip_novo="));
        assertFalse(corpoEquipe.getValue().contains(ana.getEmail()), "a equipe recebe o id, nao o e-mail do titular");
        assertFalse(corpoEquipe.getValue().contains("Ana"), "nem o nome");

        Usuario depois = usuarioRepository.findById(ana.getId()).orElseThrow();
        assertTrue(depois.isAtivo());
        assertEquals("Ana Moraes", depois.getNome());
        assertEquals(ana.getEmail(), depois.getEmail());
    }

    @Test
    @DisplayName("CT481 - Exige a senha atual, limita o motivo e limita pedidos por dia; recusas nao criam solicitacao nem enviam e-mail")
    void recusas() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, null, "errada-demais-1", null, "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, null, null, null, "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, "x".repeat(501), SENHA, null, "ip"));
        assertTrue(solicitacaoRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).isEmpty());
        Mockito.verifyNoInteractions(emailSender);

        Usuario bia = conta("Bia Moraes", PapelUsuario.CLIENTE);
        for (int i = 0; i < 3; i++) {
            service.solicitar(bia, null, SENHA, null, "ip");
            service.cancelar(bia, "ip");
        }
        assertThrows(LimiteExcedidoException.class, () -> service.solicitar(bia, null, SENHA, null, "ip"));
    }

    @Test
    @DisplayName("CT482 - Apenas uma solicitacao em andamento por usuario (conflito 409)")
    void umaPorUsuario() {
        pedir(ana, "ip");
        assertThrows(TransicaoInvalidaException.class, () -> pedir(ana, "ip"));
        assertEquals(1, solicitacaoRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).size());
    }

    @Test
    @DisplayName("CT483 - O usuario cancela quando quiser (sem senha); as restricoes terminam, o fim e auditado e ele pode pedir de novo")
    void cancelarPeloUsuario() {
        pedir(ana, "ip");
        assertFalse(restricoes.restricoesAtivas(ana).isEmpty());

        Status depois = service.cancelar(ana, "10.0.0.6");
        assertNull(depois.solicitacao());
        assertTrue(depois.restricoesAtivas().isEmpty());
        assertTrue(restricoes.restricoesAtivas(ana).isEmpty());

        List<String> acoes = auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).stream().map(AuditoriaConta::getAcao).toList();
        assertTrue(acoes.contains(AuditoriaContaService.EXCLUSAO_CANCELADA));
        assertTrue(acoes.contains(AuditoriaContaService.RESTRICOES_FIM));
        List<SolicitacaoExclusao> todas = solicitacaoRepository.findByUsuarioIdOrderByIdDesc(ana.getId());
        assertEquals(EstadoExclusao.CANCELADA_PELO_USUARIO, todas.get(0).getEstado());
        assertEquals(2, historicoRepository.findBySolicitacaoIdOrderByIdAsc(todas.get(0).getId()).size());

        assertThrows(TransicaoInvalidaException.class, () -> service.cancelar(ana, "ip"), "nada a cancelar");
        assertNotNull(pedir(ana, "ip").solicitacao(), "depois de cancelar, pode pedir de novo");
    }

    @Test
    @DisplayName("CT484 - Link Nao fui eu: cancela sem login, so uma vez; token adulterado ou lixo recebem a mesma recusa; so o hash fica no banco")
    void cancelarPorLink() {
        pedir(ana, "ip");
        confirmarTransacao();
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSender).enviar(eq(ana.getEmail()), anyString(), corpo.capture());
        Matcher m = LINK.matcher(corpo.getValue());
        assertTrue(m.find());
        String token = m.group(1);
        assertNotEquals(token, aberta(ana).getTokenCancelamentoHash());
        assertEquals(TokenSeguro.hash(token), aberta(ana).getTokenCancelamentoHash());

        String mensagem = service.cancelarPorLink(token, "9.9.9.9");
        confirmarTransacao();
        assertFalse(mensagem.isBlank());
        assertTrue(restricoes.restricoesAtivas(ana).isEmpty());
        List<String> detalhes = auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).stream()
                .map(a -> String.valueOf(a.getDetalhes())).toList();
        assertTrue(detalhes.stream().anyMatch(d -> d.contains("nao fui eu")));

        IllegalArgumentException reuso = assertThrows(IllegalArgumentException.class, () -> service.cancelarPorLink(token, "9.9.9.9"));
        IllegalArgumentException lixo = assertThrows(IllegalArgumentException.class, () -> service.cancelarPorLink("lixo", "9.9.9.9"));
        assertEquals(reuso.getMessage(), lixo.getMessage());
    }

    @Test
    @DisplayName("CT485 - Sinais de risco: senha/e-mail alterados recentemente, IP novo ou conhecido, reservas e anuncios ativos (sem dados pessoais)")
    void sinaisDeRisco() {
        auditoriaRepository.save(new AuditoriaConta(ana.getId(), AuditoriaContaService.SENHA_ALTERADA, "x", "1.1.1.1", relogio.instant().minus(Duration.ofDays(2))));
        auditoriaRepository.save(new AuditoriaConta(ana.getId(), AuditoriaContaService.LOGIN, null, "1.1.1.1", relogio.instant().minus(Duration.ofDays(3))));
        Imovel imovel = imovelDo(gestor);
        reservaDe(imovel, ana, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), StatusReserva.CONFIRMADA);

        pedir(ana, "2.2.2.2");
        String sinais = aberta(ana).getSinaisRisco();
        assertTrue(sinais.contains("senha_alterada_recentemente=SIM"), sinais);
        assertTrue(sinais.contains("email_alterado_recentemente=NAO"), sinais);
        assertTrue(sinais.contains("ip_novo=SIM"), sinais);
        assertTrue(sinais.contains("reservas_vigentes_como_cliente=1"), sinais);
        assertFalse(sinais.contains(ana.getEmail()));

        service.cancelar(ana, "2.2.2.2");
        service.solicitar(ana, null, SENHA, null, "1.1.1.1"); // IP ja conhecido
        assertTrue(aberta(ana).getSinaisRisco().contains("ip_novo=NAO"));

        service.solicitar(gestor, null, SENHA, null, "3.3.3.3");
        assertTrue(aberta(gestor).getSinaisRisco().contains("anuncios_ativos=1"));
        assertTrue(aberta(gestor).getSinaisRisco().contains("reservas_vigentes_como_gestor=1"));
    }

    // ------------------------------------------------------------------ revisao

    @Test
    @DisplayName("CT486 - Periodo minimo de analise: antes de 48h a aprovacao e barrada; depois, sem impedimentos, aprova (e nada e excluido)")
    void periodoMinimo() {
        pedir(ana, "ip");
        Long id = aberta(ana).getId();

        DataDeletionReviewService.AprovacaoBloqueadaException e = assertThrows(
                DataDeletionReviewService.AprovacaoBloqueadaException.class, () -> revisao.aprovar(id, 99L, "ok"));
        assertEquals(List.of("PERIODO_MINIMO_DE_ANALISE"), e.impedimentos());

        revisao.iniciarAnalise(id, 99L);
        relogio.avancar(Duration.ofHours(49));
        revisao.aprovar(id, 99L, "documentos conferidos");

        SolicitacaoExclusao s = solicitacaoRepository.findById(id).orElseThrow();
        assertEquals(EstadoExclusao.APROVADA, s.getEstado());
        assertEquals(99L, s.getAnalistaId());
        assertEquals("documentos conferidos", s.getObservacaoInterna());
        assertNotNull(s.getDecididaEm());
        List<EstadoExclusao> trilha = historicoRepository.findBySolicitacaoIdOrderByIdAsc(id).stream()
                .map(SolicitacaoExclusaoHistorico::getEstadoNovo).toList();
        assertEquals(List.of(EstadoExclusao.PENDENTE, EstadoExclusao.EM_ANALISE, EstadoExclusao.APROVADA), trilha);
        assertTrue(usuarioRepository.findById(ana.getId()).orElseThrow().isAtivo(), "aprovar nao exclui nada");
        assertFalse(restricoes.restricoesAtivas(ana).isEmpty(), "aprovada ainda nao concluida: restricoes continuam");
    }

    @Test
    @DisplayName("CT487 - Trava da aprovacao: reserva futura, reembolso pendente e denuncia aberta barram e voltam a lista de impedimentos")
    void impedimentos() {
        Imovel imovel = imovelDo(gestor);
        Reserva futura = reservaDe(imovel, ana, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), StatusReserva.CONFIRMADA);
        Reserva cancelada = reservaDe(imovel, ana, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), StatusReserva.CANCELADA_COM_REEMBOLSO);
        Reserva passada = reservaDe(imovel, ana, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), StatusReserva.CONCLUIDA);
        Reembolso reembolso = new Reembolso();
        reembolso.setReserva(cancelada);
        reembolso.setValor(new BigDecimal("200.00"));
        reembolso.setStatus(StatusReembolso.PENDENTE);
        reembolso.setChaveIdempotencia("reembolso-" + cancelada.getId());
        reembolso.setCriadoEm(relogio.instant());
        reembolso.setAtualizadoEm(relogio.instant());
        em.persist(reembolso);
        SmartChatConversa conversa = new SmartChatConversa();
        conversa.setCliente(ana);
        conversa.setGestor(gestor);
        conversa.setImovel(imovel);
        conversa.setCriadaEm(relogio.instant());
        em.persist(conversa);
        DenunciaChat denuncia = new DenunciaChat();
        denuncia.setConversa(conversa);
        denuncia.setDenunciante(gestor);
        denuncia.setDenunciado(ana);
        denuncia.setMotivo("GOLPE");
        denuncia.setStatus("PENDENTE");
        denuncia.setCriadaEm(relogio.instant());
        em.persist(denuncia);
        em.flush();

        pedir(ana, "ip");
        Long id = aberta(ana).getId();
        relogio.avancar(Duration.ofHours(49));

        var e = assertThrows(DataDeletionReviewService.AprovacaoBloqueadaException.class, () -> revisao.aprovar(id, 1L, null));
        String lista = String.join(" | ", e.impedimentos());
        assertTrue(lista.contains("RESERVAS_VIGENTES_COMO_CLIENTE: 1"), lista);
        assertTrue(lista.contains("REEMBOLSO_PENDENTE: 1"), lista);
        assertTrue(lista.contains("DENUNCIA_ABERTA: 1"), lista);
        assertTrue(lista.contains("CONVERSA_COM_DISPUTA: 1"), lista);
        assertEquals(EstadoExclusao.PENDENTE, solicitacaoRepository.findById(id).orElseThrow().getEstado());
        assertEquals(e.impedimentos(), revisao.impedimentos(id));

        // O gestor dono do imovel tambem tem a reserva futura como impedimento (como gestor) e a disputa.
        service.solicitar(gestor, null, SENHA, null, "ip");
        List<String> doGestor = revisao.impedimentos(aberta(gestor).getId());
        assertTrue(doGestor.stream().anyMatch(i -> i.startsWith("RESERVAS_VIGENTES_COMO_GESTOR")), doGestor.toString());
        assertTrue(doGestor.stream().anyMatch(i -> i.startsWith("REEMBOLSO_PENDENTE")), doGestor.toString());
        assertTrue(doGestor.stream().anyMatch(i -> i.startsWith("CONVERSA_COM_DISPUTA")), doGestor.toString());

        assertNotNull(passada);
        assertNotNull(futura);
    }

    @Test
    @DisplayName("CT488 - Negar encerra as restricoes (e nao nega duas vezes); concluir chama o executor e encerra as restricoes")
    void negarEConcluir() {
        pedir(ana, "ip");
        Long id = aberta(ana).getId();
        revisao.negar(id, 7L, "pedido suspeito");
        assertEquals(EstadoExclusao.NEGADA, solicitacaoRepository.findById(id).orElseThrow().getEstado());
        assertTrue(restricoes.restricoesAtivas(ana).isEmpty());
        assertTrue(auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).stream()
                .anyMatch(a -> AuditoriaContaService.RESTRICOES_FIM.equals(a.getAcao())));
        assertThrows(TransicaoInvalidaException.class, () -> revisao.negar(id, 7L, "de novo"));

        Usuario bia = conta("Bia Moraes", PapelUsuario.CLIENTE);
        service.solicitar(bia, null, SENHA, null, "ip");
        Long idBia = aberta(bia).getId();
        relogio.avancar(Duration.ofHours(49));
        revisao.aprovar(idBia, 7L, null);
        revisao.concluir(idBia, 7L); // com o executor falso (mock) disponivel
        Mockito.verify(executorFalso).executar(bia.getId());
        assertEquals(EstadoExclusao.CONCLUIDA, solicitacaoRepository.findById(idBia).orElseThrow().getEstado());
        assertTrue(restricoes.restricoesAtivas(bia).isEmpty(), "concluida: fim das restricoes");
    }
}
