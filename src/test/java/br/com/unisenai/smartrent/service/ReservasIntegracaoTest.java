package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.config.ReservaProperties;
import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioPedido;
import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioResposta;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Pedido;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Resposta;
import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AuditoriaAnuncioRepository;
import br.com.unisenai.smartrent.repository.PagamentoRepository;
import br.com.unisenai.smartrent.repository.ReembolsoRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException;
import br.com.unisenai.smartrent.service.erro.PagamentoRecusadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluxos de reserva, pagamento, cancelamento, reembolso e bloqueio de datas com
 * banco de verdade (H2), gateway falso e relogio controlado. Cobre idempotencia,
 * falha de gateway sem desfazer o cancelamento e isolamento entre usuarios.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fluxo;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({ReservaService.class, ReservaClienteService.class, ReservaClienteMapper.class, CancelamentoService.class,
        ReembolsoService.class, RefundPolicyService.class, BloqueioService.class, CalendarioService.class,
        AuditoriaService.class, NotificacaoService.class, ImovelAcesso.class, TextosPoliticas.class,
        NotificadorEmailLog.class, ReservasIntegracaoTest.Config.class})
class ReservasIntegracaoTest {

    /** Gateway controlavel: conta chamadas e pode falhar o estorno. */
    static class GatewayFalso implements GatewayPagamento {
        int cobrancas;
        int estornos;
        boolean falharEstorno;
        final Map<String, Resultado> porChave = new HashMap<>();

        @Override
        public Resultado cobrar(String chave, BigDecimal valor, String moeda, String token) {
            cobrancas++;
            return porChave.computeIfAbsent("c" + chave, k ->
                    "recusado".equals(token) ? Resultado.recusado("Recusado") : Resultado.ok("C-" + chave));
        }

        @Override
        public Resultado estornar(String chave, String ref, BigDecimal valor) {
            estornos++;
            if (falharEstorno) {
                throw new IllegalStateException("gateway fora do ar");
            }
            return porChave.computeIfAbsent("e" + chave, k -> Resultado.ok("E-" + chave));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0)); // 12:00 em Brasilia
        }

        @Bean
        ZoneId zona() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Bean
        PoliticaCancelamentoProperties politica() {
            return PoliticaCancelamentoProperties.padrao();
        }

        @Bean
        ReservaProperties reservaProps() {
            return new ReservaProperties(30);
        }

        @Bean
        GatewayFalso gateway() {
            return new GatewayFalso();
        }
    }

    @Autowired private TestEntityManager em;
    @Autowired private RelogioFalso relogio;
    @Autowired private GatewayFalso gateway;
    @Autowired private ReservaClienteService clienteService;
    @Autowired private ReservaService reservaService;
    @Autowired private CancelamentoService cancelamento;
    @Autowired private ReembolsoService reembolsoService;
    @Autowired private BloqueioService bloqueioService;
    @Autowired private CalendarioService calendarioService;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private ReembolsoRepository reembolsoRepository;
    @Autowired private AuditoriaAnuncioRepository auditoriaRepository;

    private Usuario gestor;
    private Usuario outroGestor;
    private Usuario cliente;
    private Usuario outroCliente;
    private Imovel imovel;

    private static final LocalDate LONGE = LocalDate.of(2026, 12, 10);
    private static final LocalDate LONGE_FIM = LocalDate.of(2026, 12, 13);

    private Usuario usuario(String nome, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(papel);
        u.setAtivo(true);
        return em.persist(u);
    }

    @BeforeEach
    void preparar() {
        gateway.cobrancas = 0;
        gateway.estornos = 0;
        gateway.falharEstorno = false;
        gestor = usuario("Gestor Um", PapelUsuario.ANFITRIAO);
        outroGestor = usuario("Gestor Dois", PapelUsuario.ANFITRIAO);
        cliente = usuario("Cliente Um", PapelUsuario.CLIENTE);
        outroCliente = usuario("Cliente Dois", PapelUsuario.CLIENTE);

        imovel = new Imovel();
        imovel.setUsuario(gestor);
        imovel.setTitulo("Apto Canasvieiras");
        imovel.setTipoImovel(TipoImovel.APARTAMENTO);
        imovel.setCapacidadeHospedes(4);
        imovel.setNumeroQuartos(2);
        imovel.setNumeroBanheiros(1);
        imovel.setValorDiariaBase(new BigDecimal("300.00"));
        imovel.setTaxaLimpeza(new BigDecimal("80.00"));
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        imovel.setEndereco(e);
        em.persist(imovel);
        em.flush();
    }

    private Pedido pedido(LocalDate in, LocalDate out, boolean ciente) {
        return new Pedido(imovel.getId(), in, out, 2, null, ciente);
    }

    private Resposta reservarEPagar(LocalDate in, LocalDate out) {
        Resposta r = clienteService.criar(cliente, pedido(in, out, true));
        return clienteService.pagar(cliente, r.id(), "ok");
    }

    // ---------------------------------------------------------------- pagamento

    @Test
    @DisplayName("CT310 - Cliente reserva, paga e a reserva confirma com total = diarias + taxa de limpeza")
    void criarEPagar() {
        Resposta r = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));
        assertEquals(StatusReserva.PENDENTE, r.status());
        assertEquals(new BigDecimal("980.00"), r.total()); // 3 x 300 + 80
        assertTrue(r.podePagar());

        Resposta paga = clienteService.pagar(cliente, r.id(), "ok");
        assertEquals(StatusReserva.CONFIRMADA, paga.status());
        assertEquals(1, pagamentoRepository.countByReservaId(r.id()));
    }

    @Test
    @DisplayName("CT311 - Pagar duas vezes (clique duplo, retry) nao cobra duas vezes")
    void pagamentoIdempotente() {
        Resposta r = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));
        clienteService.pagar(cliente, r.id(), "ok");
        clienteService.pagar(cliente, r.id(), "ok");
        clienteService.pagar(cliente, r.id(), "ok");

        assertEquals(1, gateway.cobrancas);
        assertEquals(1, pagamentoRepository.countByReservaId(r.id()));
    }

    @Test
    @DisplayName("CT312 - Pagamento recusado fica registrado, a reserva segue pendente e uma nova tentativa funciona")
    void pagamentoRecusado() {
        Resposta r = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));
        assertThrows(PagamentoRecusadoException.class, () -> clienteService.pagar(cliente, r.id(), "recusado"));
        em.flush();
        assertEquals(StatusReserva.PENDENTE, reservaRepository.findById(r.id()).orElseThrow().getStatus());
        assertEquals(StatusPagamento.RECUSADO, pagamentoRepository.findAll().get(0).getStatus());

        assertEquals(StatusReserva.CONFIRMADA, clienteService.pagar(cliente, r.id(), "ok").status());
        assertEquals(2, pagamentoRepository.countByReservaId(r.id()));
    }

    @Test
    @DisplayName("CT313 - Cliente so paga, le e cancela as proprias reservas")
    void isolamentoDeClientes() {
        Resposta r = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));

        assertThrows(AcessoNegadoException.class, () -> clienteService.pagar(outroCliente, r.id(), "ok"));
        assertThrows(AcessoNegadoException.class, () -> clienteService.buscar(outroCliente, r.id()));
        assertThrows(AcessoNegadoException.class, () -> cancelamento.cancelarPeloCliente(outroCliente, r.id(), null));
        assertThrows(AcessoNegadoException.class, () -> cancelamento.simular(outroCliente, r.id()));
        assertTrue(clienteService.listar(outroCliente).isEmpty());
    }

    @Test
    @DisplayName("CT314 - Reserva criada com menos de 48h do check-in exige ciencia de que nao ha reembolso")
    void avisoSemReembolsoAntesDoPagamento() {
        LocalDate amanha = LocalDate.of(2026, 10, 4); // hoje em Brasilia: 03/10 12:00
        var erro = assertThrows(ConflitoComDetalhesException.class,
                () -> clienteService.criar(cliente, pedido(amanha, amanha.plusDays(2), false)));
        assertEquals("CONFIRMAR_SEM_REEMBOLSO", erro.codigo());

        Resposta r = clienteService.criar(cliente, pedido(amanha, amanha.plusDays(2), true));
        assertTrue(r.politica().semDireitoAReembolso());
    }

    @Test
    @DisplayName("CT315 - Pendente nao paga expira e libera as datas; nao expira antes do prazo")
    void pendenteExpira() {
        Resposta r = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));

        relogio.avancar(Duration.ofMinutes(29));
        assertEquals(0, cancelamento.expirarPendentes());

        relogio.avancar(Duration.ofMinutes(2));
        assertEquals(1, cancelamento.expirarPendentes());
        Reserva salva = reservaRepository.findById(r.id()).orElseThrow();
        assertEquals(StatusReserva.CANCELADA_SEM_REEMBOLSO, salva.getStatus());
        assertEquals("PENDENTE_SEM_COBRANCA", salva.getCancelamentoRegra());
        assertEquals(0, gateway.estornos);
        assertDoesNotThrow(() -> clienteService.criar(outroCliente, pedido(LONGE, LONGE_FIM, false)),
                "as datas voltam a ficar livres");
    }

    // -------------------------------------------------------------- cancelamento

    @Test
    @DisplayName("CT320 - Cancelamento com mais de 48h: reembolso integral processado uma unica vez, datas liberadas")
    void cancelaComReembolso() {
        Resposta paga = reservarEPagar(LONGE, LONGE_FIM);

        var sim = cancelamento.simular(cliente, paga.id());
        assertTrue(sim.comReembolso());
        assertEquals(new BigDecimal("980.00"), sim.valorReembolso());

        Reserva c = cancelamento.cancelarPeloCliente(cliente, paga.id(), "mudei de planos");
        assertEquals(StatusReserva.CANCELADA_COM_REEMBOLSO, c.getStatus());
        assertEquals("CLIENTE", c.getCanceladaPor());
        Reembolso reembolso = reembolsoRepository.findByReservaId(paga.id()).orElseThrow();
        assertEquals(StatusReembolso.PROCESSADO, reembolso.getStatus());
        assertEquals(new BigDecimal("980.00"), reembolso.getValor());

        // clique duplo / retry: nao estorna de novo
        cancelamento.cancelarPeloCliente(cliente, paga.id(), "mudei de planos");
        assertEquals(1, gateway.estornos);

        // snapshot de preco intacto e datas liberadas
        assertEquals(new BigDecimal("980.00"), reservaRepository.findById(paga.id()).orElseThrow().getTotalSnapshot());
        assertDoesNotThrow(() -> clienteService.criar(outroCliente, pedido(LONGE, LONGE_FIM, false)));
    }

    @Test
    @DisplayName("CT321 - Cancelamento com menos de 48h: cancela sem reembolso e sem chamar o gateway")
    void cancelaSemReembolso() {
        LocalDate amanha = LocalDate.of(2026, 10, 4);
        Resposta paga = reservarEPagar(amanha, amanha.plusDays(2));

        var sim = cancelamento.simular(cliente, paga.id());
        assertFalse(sim.comReembolso());

        Reserva c = cancelamento.cancelarPeloCliente(cliente, paga.id(), null);
        assertEquals(StatusReserva.CANCELADA_SEM_REEMBOLSO, c.getStatus());
        assertTrue(reembolsoRepository.findByReservaId(paga.id()).isEmpty());
        assertEquals(0, gateway.estornos);
    }

    @Test
    @DisplayName("CT322 - A simulacao exibida coincide com a decisao gravada ao confirmar")
    void simulacaoCoincideComDecisao() {
        Resposta paga = reservarEPagar(LONGE, LONGE_FIM);
        var sim = cancelamento.simular(cliente, paga.id());
        Reserva c = cancelamento.cancelarPeloCliente(cliente, paga.id(), null);

        assertEquals(sim.regra(), c.getCancelamentoRegra());
        assertEquals(sim.comReembolso(), c.getStatus() == StatusReserva.CANCELADA_COM_REEMBOLSO);
    }

    @Test
    @DisplayName("CT323 - Falha no gateway nao desfaz o cancelamento: reembolso FALHA, depois retry processa")
    void falhaNoGatewayMantemOCancelamento() {
        Resposta paga = reservarEPagar(LONGE, LONGE_FIM);
        gateway.falharEstorno = true;

        Reserva c = cancelamento.cancelarPeloCliente(cliente, paga.id(), null);

        assertEquals(StatusReserva.CANCELADA_COM_REEMBOLSO, c.getStatus());
        Reembolso r = reembolsoRepository.findByReservaId(paga.id()).orElseThrow();
        assertEquals(StatusReembolso.FALHA, r.getStatus());
        assertEquals(1, r.getTentativas());
        assertNotNull(r.getErro());
        assertNotNull(r.getProximaTentativaEm());
        // o gestor foi alertado (in-app) na primeira falha
        assertTrue(em.getEntityManager().createQuery(
                "select count(n) from Notificacao n where n.usuario.id = :g and n.titulo like 'Reembolso%'", Long.class)
                .setParameter("g", gestor.getId()).getSingleResult() > 0);

        // ainda dentro da espera: nada a fazer; depois da espera e com o gateway de volta: processa
        assertEquals(0, reembolsoService.reprocessar());
        gateway.falharEstorno = false;
        relogio.avancar(Duration.ofMinutes(10));
        assertEquals(1, reembolsoService.reprocessar());
        assertEquals(StatusReembolso.PROCESSADO, reembolsoRepository.findByReservaId(paga.id()).orElseThrow().getStatus());
        assertEquals(0, reembolsoService.reprocessar(), "reprocessar de novo nao estorna duas vezes");
    }

    @Test
    @DisplayName("CT324 - Gestor cancela: motivo obrigatorio, reembolso integral, auditoria; so o dono do imovel cancela")
    void gestorCancela() {
        LocalDate amanha = LocalDate.of(2026, 10, 4);
        Resposta paga = reservarEPagar(amanha, amanha.plusDays(2)); // dentro das 48h: so o gestor garante o reembolso

        assertThrows(IllegalArgumentException.class, () -> cancelamento.cancelarPeloGestor(gestor, paga.id(), "  "));
        assertThrows(AcessoNegadoException.class, () -> cancelamento.cancelarPeloGestor(outroGestor, paga.id(), "x"));

        Reserva c = cancelamento.cancelarPeloGestor(gestor, paga.id(), "imóvel interditado");
        assertEquals(StatusReserva.CANCELADA_PELO_GESTOR, c.getStatus());
        assertEquals(StatusReembolso.PROCESSADO, reembolsoRepository.findByReservaId(paga.id()).orElseThrow().getStatus());
        assertTrue(auditoriaRepository.findAll().stream().anyMatch(a -> "RESERVA_CANCELADA".equals(a.getAcao())
                && a.getDetalhes().contains("GESTOR_CANCELA")));
    }

    // ------------------------------------------------------------------ bloqueio

    private BloqueioPedido bloqueio(LocalDate in, LocalDate out, boolean apenasLivres, boolean cancelarPendentes) {
        return new BloqueioPedido(in, out, "MANUTENCAO", "pintura", apenasLivres, cancelarPendentes);
    }

    @Test
    @DisplayName("CT330 - Nao bloqueia datas com reserva confirmada: erro com as datas; com 'apenas livres' bloqueia o resto")
    void bloqueioComReservaConfirmada() {
        reservarEPagar(LONGE, LONGE_FIM); // noites 10, 11 e 12/12
        var erro = assertThrows(ConflitoComDetalhesException.class,
                () -> bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE.minusDays(2), LONGE.plusDays(4), false, false)));
        assertEquals("RESERVA_CONFIRMADA_NO_PERIODO", erro.codigo());
        assertTrue(erro.getMessage().contains("2026-12-10 a 2026-12-12"), erro.getMessage());

        List<BloqueioResposta> criados = bloqueioService.criar(gestor, imovel.getId(),
                bloqueio(LONGE.minusDays(2), LONGE.plusDays(4), true, false));
        assertEquals(2, criados.size()); // antes e depois da reserva
        assertEquals(LONGE.minusDays(1), criados.get(0).dataFim());
        assertEquals(LONGE_FIM, criados.get(1).dataInicio());
    }

    @Test
    @DisplayName("CT331 - Datas bloqueadas ficam indisponiveis para reservas e o cliente so recebe 'indisponivel'")
    void bloqueioIndisponibilizaDatas() {
        bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE, LONGE.plusDays(1), false, false));

        var erro = assertThrows(IllegalArgumentException.class,
                () -> clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false)));
        assertEquals(ReservaService.MSG_INDISPONIVEL, erro.getMessage());
        assertFalse(erro.getMessage().toLowerCase().contains("bloque"), "o cliente nao descobre que e bloqueio manual");
        var gestorErro = assertThrows(IllegalArgumentException.class, () -> reservaService.criar(gestor,
                new ReservaRequest(imovel.getId(), "Hospede", "h@e.com", null, LONGE, LONGE_FIM, null,
                        StatusReserva.CONFIRMADA, null, null, 2)));
        assertEquals(ReservaService.MSG_INDISPONIVEL, gestorErro.getMessage());

        var faixas = calendarioService.indisponibilidade(imovel.getId(), LONGE.minusDays(5), LONGE.plusDays(20));
        assertEquals(1, faixas.size());
        assertEquals(LONGE, faixas.get(0).inicio());
        assertEquals(LONGE.plusDays(1), faixas.get(0).fim());
    }

    @Test
    @DisplayName("CT332 - Remocao total e parcial do bloqueio libera as datas; remover o miolo divide em dois")
    void removerBloqueio() {
        BloqueioResposta b = bloqueioService.criar(gestor, imovel.getId(),
                bloqueio(LONGE, LONGE.plusDays(5), false, false)).get(0);

        List<BloqueioResposta> partes = bloqueioService.remover(gestor, imovel.getId(), b.id(),
                LONGE.plusDays(2), LONGE.plusDays(3));
        assertEquals(2, partes.size());
        assertEquals(LONGE.plusDays(1), partes.get(0).dataFim());
        assertEquals(LONGE.plusDays(4), partes.get(1).dataInicio());
        assertEquals("pintura", partes.get(1).observacao(), "motivo e observacao preservados nas partes");
        assertDoesNotThrow(() -> clienteService.criar(cliente, pedido(LONGE.plusDays(2), LONGE.plusDays(4), false)));

        for (BloqueioResposta p : partes) {
            assertTrue(bloqueioService.remover(gestor, imovel.getId(), p.id(), null, null).isEmpty());
        }
        assertTrue(bloqueioService.listar(gestor, imovel.getId()).isEmpty());
    }

    @Test
    @DisplayName("CT333 - Reserva pendente no periodo so e cancelada (sem cobranca) se o gestor confirmar")
    void bloqueioComPendente() {
        Resposta pendente = clienteService.criar(cliente, pedido(LONGE, LONGE_FIM, false));

        var erro = assertThrows(ConflitoComDetalhesException.class,
                () -> bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE, LONGE_FIM, false, false)));
        assertEquals("RESERVAS_PENDENTES_NO_PERIODO", erro.codigo());
        assertEquals(StatusReserva.PENDENTE, reservaRepository.findById(pendente.id()).orElseThrow().getStatus());

        bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE, LONGE_FIM, false, true));
        em.flush();
        Reserva r = reservaRepository.findById(pendente.id()).orElseThrow();
        assertEquals(StatusReserva.CANCELADA_SEM_REEMBOLSO, r.getStatus());
        assertEquals(0, gateway.estornos);
    }

    @Test
    @DisplayName("CT334 - Bloqueio nao altera reservas nem snapshots existentes e e auditado (criacao e remocao)")
    void bloqueioNaoAlteraSnapshotEAudita() {
        Resposta paga = reservarEPagar(LONGE, LONGE_FIM);
        BigDecimal totalAntes = reservaRepository.findById(paga.id()).orElseThrow().getTotalSnapshot();

        BloqueioResposta b = bloqueioService.criar(gestor, imovel.getId(),
                bloqueio(LONGE.plusDays(10), LONGE.plusDays(12), false, false)).get(0);
        bloqueioService.remover(gestor, imovel.getId(), b.id(), null, null);

        Reserva depois = reservaRepository.findById(paga.id()).orElseThrow();
        assertEquals(totalAntes, depois.getTotalSnapshot());
        assertEquals(StatusReserva.CONFIRMADA, depois.getStatus());
        assertTrue(auditoriaRepository.findAll().stream().anyMatch(a -> "BLOQUEIO_CRIADO".equals(a.getAcao())));
        assertTrue(auditoriaRepository.findAll().stream().anyMatch(a -> "BLOQUEIO_REMOVIDO".equals(a.getAcao())));
    }

    @Test
    @DisplayName("CT335 - Bloqueios e calendario de um gestor nao sao acessiveis a outro; o calendario traz so o imovel pedido")
    void isolamentoEntreGestores() {
        BloqueioResposta b = bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE, LONGE.plusDays(1), false, false)).get(0);
        reservarEPagar(LONGE.plusDays(10), LONGE.plusDays(12));

        assertThrows(AcessoNegadoException.class, () -> bloqueioService.criar(outroGestor, imovel.getId(), bloqueio(LONGE, LONGE.plusDays(1), false, false)));
        assertThrows(AcessoNegadoException.class, () -> bloqueioService.remover(outroGestor, imovel.getId(), b.id(), null, null));
        assertThrows(AcessoNegadoException.class, () -> bloqueioService.listar(outroGestor, imovel.getId()));
        assertThrows(AcessoNegadoException.class, () -> calendarioService.calendario(outroGestor, imovel.getId(), LONGE, LONGE.plusDays(30)));
        assertThrows(AcessoNegadoException.class, () -> calendarioService.calendario(cliente, imovel.getId(), LONGE, LONGE.plusDays(30)));

        var cal = calendarioService.calendario(gestor, imovel.getId(), LONGE.minusDays(3), LONGE.plusDays(30));
        assertEquals(1, cal.reservas().size());
        assertEquals(cliente.getEmail(), cal.reservas().get(0).hospedeEmail());
        assertEquals(2, cal.reservas().get(0).numeroHospedes());
        assertEquals(1, cal.bloqueios().size());
        assertEquals("2026-10-03", cal.hoje().toString());
    }

    @Test
    @DisplayName("CT336 - Bloqueio persiste e continua valendo quando o imovel entra em edicao")
    void bloqueioPersisteComImovelEmEdicao() {
        bloqueioService.criar(gestor, imovel.getId(), bloqueio(LONGE, LONGE.plusDays(1), false, false));
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        em.persist(imovel);
        em.flush();

        assertEquals(1, bloqueioService.listar(gestor, imovel.getId()).size());
        assertEquals(1, calendarioService.calendario(gestor, imovel.getId(), LONGE, LONGE.plusDays(5)).bloqueios().size());
    }

    @Test
    @DisplayName("CT337 - Validacoes do bloqueio: motivo valido, periodo coerente, sem datas passadas")
    void validacoesDoBloqueio() {
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.criar(gestor, imovel.getId(),
                new BloqueioPedido(LONGE, LONGE.plusDays(1), "QUALQUER", null, false, false)));
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.criar(gestor, imovel.getId(),
                bloqueio(LONGE.plusDays(3), LONGE, false, false)));
        assertThrows(IllegalArgumentException.class, () -> bloqueioService.criar(gestor, imovel.getId(),
                bloqueio(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), false, false)));
    }

    // ------------------------------------------------------------ reserva do gestor

    @Test
    @DisplayName("CT340 - Reserva do gestor para e-mail de um cliente cadastrado vincula o cliente; cancelar exige a acao propria")
    void reservaDoGestorVinculaClienteEBloqueiaStatusCancelado() {
        Reserva r = reservaService.criar(gestor, new ReservaRequest(imovel.getId(), "Cliente Um", cliente.getEmail(),
                null, LONGE, LONGE_FIM, null, StatusReserva.CONFIRMADA, null, null, 2));
        assertNotNull(r.getCliente());
        assertEquals(cliente.getId(), r.getCliente().getId());
        assertEquals(new BigDecimal("980.00"), r.getTotalSnapshot());

        assertThrows(IllegalArgumentException.class, () -> reservaService.atualizar(gestor, r.getId(),
                new ReservaRequest(imovel.getId(), "Cliente Um", cliente.getEmail(), null, LONGE, LONGE_FIM, null,
                        StatusReserva.CANCELADA_PELO_GESTOR, null, null, 2)));
        assertThrows(IllegalArgumentException.class, () -> reservaService.criar(gestor,
                new ReservaRequest(imovel.getId(), "Fulano", "f@e.com", null, LONGE.plusDays(20), LONGE.plusDays(22), null,
                        StatusReserva.CANCELADA_SEM_REEMBOLSO, null, null, 1)));
    }

    @Test
    @DisplayName("CT341 - Reserva de cliente nao pode ser excluida (ha pagamento e historico): so cancelada")
    void reservaDeClienteNaoSeExclui() {
        Resposta paga = reservarEPagar(LONGE, LONGE_FIM);
        assertThrows(br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException.class,
                () -> reservaService.excluir(gestor, paga.id()));
        List<Reserva> todas = new ArrayList<>(reservaRepository.findAll());
        assertEquals(1, todas.size());
    }
}
