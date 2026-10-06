package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Conversa;
import br.com.unisenai.smartrent.dto.SmartChatDtos.DenunciaPedido;
import br.com.unisenai.smartrent.dto.SmartChatDtos.EnvioResposta;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Mensagem;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Perfil;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Notificacao;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.SmartChatMensagem;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.model.enums.TipoMensagem;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.BloqueioUsuarioRepository;
import br.com.unisenai.smartrent.repository.SmartChatConversaRepository;
import br.com.unisenai.smartrent.repository.SmartChatMensagemRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SmartChat com banco de verdade (H2): conversa unica, isolamento, criacao por reserva idempotente, filtro e notificacoes. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartchat;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({SmartChatService.class, MessageFilterService.class, ChatEventos.class, LimitadorDeTaxa.class,
        NotificacaoService.class, NotificadorEmailLog.class, TextosPoliticas.class, ChatReconciliador.class,
        AnaliseComportamentoChat.class, SmartChatIntegracaoTest.Config.class})
class SmartChatIntegracaoTest {

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0));
        }

        @Bean
        ChatProperties chatProps() {
            return ChatProperties.padrao();
        }

        @Bean
        br.com.unisenai.smartrent.config.ChatSegurancaProperties chatSegurancaProps() {
            return br.com.unisenai.smartrent.config.ChatSegurancaProperties.padrao();
        }

        @Bean
        br.com.unisenai.smartrent.security.HmacTexto hmacTexto() {
            return new br.com.unisenai.smartrent.security.HmacTexto(new byte[32]);
        }

        @Bean
        br.com.unisenai.smartrent.security.CifraCampo cifraCampo() {
            return new br.com.unisenai.smartrent.security.CifraCampo(new byte[32]);
        }
    }

    @Autowired private TestEntityManager em;
    @Autowired private RelogioFalso relogio;
    @Autowired private SmartChatService chat;
    @Autowired private ChatReconciliador reconciliador;
    @Autowired private SmartChatConversaRepository conversas;
    @Autowired private SmartChatMensagemRepository mensagens;
    @Autowired private DenunciaChatRepository denuncias;
    @Autowired private BloqueioUsuarioRepository bloqueios;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Usuario gestor;
    private Usuario outroGestor;
    private Usuario cliente;
    private Usuario outroCliente;
    private Imovel imovel;

    private Usuario usuario(String nome, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(papel);
        u.setAtivo(true);
        u.setEmailVerificadoEm(relogio.instant()); // a regra de e-mail verificado tem testes proprios
        return em.persist(u);
    }

    private Imovel novoImovel(Usuario dono, StatusAnuncio status) {
        Imovel i = new Imovel();
        i.setUsuario(dono);
        i.setTitulo("Apto Canasvieiras");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(4);
        i.setNumeroQuartos(2);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setStatus(status);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        return em.persist(i);
    }

    private Reserva reserva(Usuario hospede, Imovel imovel, StatusReserva status, int diasAFrente) {
        Reserva r = new Reserva();
        r.setImovel(imovel);
        r.setCliente(hospede);
        r.setHospedeNome(hospede.getNome());
        r.setHospedeEmail(hospede.getEmail());
        r.setDataCheckin(LocalDate.of(2026, 12, 10).plusDays(diasAFrente));
        r.setDataCheckout(LocalDate.of(2026, 12, 13).plusDays(diasAFrente));
        r.setStatus(status);
        r.setNumeroDiarias(3);
        r.setNumeroHospedes(2);
        r.setPrecoDiariaSnapshot(new BigDecimal("300.00"));
        r.setTotalSnapshot(new BigDecimal("900.00"));
        r.setValorTotal(new BigDecimal("900.00"));
        r.setLimiteHospedesSnapshot(4);
        r.setImovelTituloSnapshot("Apto Canasvieiras");
        r.setDataCriacao(LocalDateTime.of(2026, 10, 3, 12, 0));
        return em.persist(r);
    }

    @BeforeEach
    void preparar() {
        gestor = usuario("Gestor Um", PapelUsuario.ANFITRIAO);
        outroGestor = usuario("Gestor Dois", PapelUsuario.ANFITRIAO);
        cliente = usuario("Maria Souza", PapelUsuario.CLIENTE);
        outroCliente = usuario("Joao Lima", PapelUsuario.CLIENTE);
        imovel = novoImovel(gestor, StatusAnuncio.PUBLICADO);
        em.flush();
    }

    // -------------------------------------------------------------- abertura

    @Test
    @DisplayName("CT500 - Cliente abre a conversa pelo anuncio; a aba e liberada no backend e a conversa e unica")
    void abrirPorImovelELiberarAba() {
        assertFalse(cliente.isSmartchatLiberado());
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        assertTrue(cliente.isSmartchatLiberado());
        assertEquals("CLIENTE", c.meuPapel());
        assertEquals("Gestor", c.interlocutor().papel());
        assertEquals("IMV-%06d".formatted(imovel.getId()), c.imovel().codigo());

        Conversa de_novo = chat.abrirPorImovel(cliente, imovel.getId());
        assertEquals(c.codigo(), de_novo.codigo());
        assertEquals(1, conversas.count());
    }

    @Test
    @DisplayName("CT501 - Cada cliente tem a sua conversa por imovel; gestor nao inicia conversa pelo anuncio")
    void conversasPorCliente() {
        Conversa a = chat.abrirPorImovel(cliente, imovel.getId());
        Conversa b = chat.abrirPorImovel(outroCliente, imovel.getId());
        assertNotEquals(a.codigo(), b.codigo());
        assertThrows(AcessoNegadoException.class, () -> chat.abrirPorImovel(gestor, imovel.getId()));
        assertThrows(AcessoNegadoException.class, () -> chat.abrirPorImovel(null, imovel.getId()));
    }

    @Test
    @DisplayName("CT502 - O botao do anuncio exige anuncio publicado; pela reserva a conversa abre mesmo com o anuncio fora do ar")
    void anuncioForaDoAr() {
        Imovel fora = novoImovel(gestor, StatusAnuncio.EM_EDICAO);
        assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.abrirPorImovel(cliente, fora.getId()));

        Reserva r = reserva(cliente, fora, StatusReserva.CONFIRMADA, 0);
        Conversa c = chat.abrirPorReserva(cliente, r.getId());
        assertFalse(c.anuncioPublicado());
        assertEquals(c.codigo(), chat.abrirPorReserva(gestor, r.getId()).codigo(), "gestor e cliente chegam na mesma conversa");
    }

    @Test
    @DisplayName("CT503 - Pela reserva so o cliente dono e o gestor do imovel abrem a conversa")
    void abrirPorReservaExigeParticipante() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        assertThrows(AcessoNegadoException.class, () -> chat.abrirPorReserva(outroCliente, r.getId()));
        assertThrows(AcessoNegadoException.class, () -> chat.abrirPorReserva(outroGestor, r.getId()));
    }

    // ------------------------------------------------ criacao na confirmacao

    @Test
    @DisplayName("CT510 - Confirmar reserva sem conversa previa cria o chat, libera a aba e registra a mensagem de sistema")
    void confirmacaoCriaChat() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);

        chat.garantirConversaDaReserva(r.getId());

        assertTrue(cliente.isSmartchatLiberado());
        assertEquals(1, conversas.count());
        List<SmartChatMensagem> msgs = mensagens.findAll();
        assertEquals(1, msgs.size());
        SmartChatMensagem m = msgs.get(0);
        assertEquals(TipoMensagem.SISTEMA, m.getTipo());
        assertNull(m.getAutor());
        assertTrue(m.getTextoFiltrado().startsWith("Reserva confirmada. Use este chat para tratar detalhes com o gestor."));
        assertTrue(m.getTextoFiltrado().contains("10/12/2026") && m.getTextoFiltrado().contains("13/12/2026"));
        assertTrue(m.getTextoFiltrado().contains("2 hóspedes"));
        assertTrue(m.getTextoFiltrado().contains("IMV-%06d".formatted(imovel.getId())), "o codigo da plataforma nao e borrado");
        assertTrue(conversas.findAll().get(0).getReservaIds().contains(r.getId()));
        // cliente e gestor foram avisados com link direto
        List<Notificacao> avisos = em.getEntityManager().createQuery("select n from Notificacao n", Notificacao.class).getResultList();
        assertEquals(2, avisos.size());
        assertTrue(avisos.stream().allMatch(n -> n.getLink().startsWith("/smartchat.html?conversa=")));
    }

    @Test
    @DisplayName("CT511 - Confirmacao repetida (retry, webhook, concorrencia) nao duplica conversa, mensagem nem aviso")
    void confirmacaoIdempotente() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        chat.garantirConversaDaReserva(r.getId());
        chat.garantirConversaDaReserva(r.getId());
        chat.garantirConversaDaReserva(r.getId());

        assertEquals(1, conversas.count());
        assertEquals(1, mensagens.count());
        assertEquals(2, em.getEntityManager().createQuery("select count(n) from Notificacao n", Long.class).getSingleResult());
    }

    @Test
    @DisplayName("CT512 - Com conversa ja existente, a confirmacao reutiliza a conversa; duas reservas = duas mensagens de sistema")
    void confirmacaoReutilizaConversa() {
        Conversa existente = chat.abrirPorImovel(cliente, imovel.getId());
        Reserva r1 = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        Reserva r2 = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 30);

        chat.garantirConversaDaReserva(r1.getId());
        chat.garantirConversaDaReserva(r2.getId());

        assertEquals(1, conversas.count());
        assertEquals(existente.codigo(), conversas.findAll().get(0).getCodigoPublico());
        assertEquals(2, mensagens.count());
    }

    @Test
    @DisplayName("CT513 - Reserva de hospede sem conta nao cria conversa (nao ha cliente) e nao falha")
    void reservaSemCliente() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        r.setCliente(null);
        em.persist(r);
        assertDoesNotThrow(() -> chat.garantirConversaDaReserva(r.getId()));
        assertEquals(0, conversas.count());
    }

    @Test
    @DisplayName("CT514 - Cancelamento registra mensagem de sistema com quem cancelou e o resultado, sem apagar o historico")
    void cancelamentoNoChat() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        chat.garantirConversaDaReserva(r.getId());
        chat.enviar(cliente, conversas.findAll().get(0).getCodigoPublico(), "Chegamos às 14h");

        r.setStatus(StatusReserva.CANCELADA_COM_REEMBOLSO);
        r.setCanceladaPor("CLIENTE");
        r.setCancelamentoRegra("REEMBOLSO_INTEGRAL_ANTECEDENCIA");
        em.persist(r);
        chat.registrarCancelamento(r.getId());
        chat.registrarCancelamento(r.getId()); // repetido: nao duplica

        List<SmartChatMensagem> todas = mensagens.findAll();
        assertEquals(3, todas.size(), "confirmacao + mensagem do cliente + cancelamento");
        String texto = todas.get(2).getTextoFiltrado();
        assertTrue(texto.contains("cancelada pelo cliente") && texto.contains("reembolso integral"), texto);
        assertFalse(texto.toLowerCase().contains("cartão") || texto.contains("R$"), "sem dados de pagamento");
        assertEquals(TipoMensagem.SISTEMA, todas.get(2).getTipo());
    }

    @Test
    @DisplayName("CT515 - Reconciliacao cria o que ficou para tras (conversa de reserva confirmada e mensagem de cancelamento)")
    void reconciliacao() {
        Reserva confirmada = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        Reserva cancelada = reserva(outroCliente, imovel, StatusReserva.CANCELADA_SEM_REEMBOLSO, 10);
        cancelada.setCanceladaPor("CLIENTE");
        cancelada.setCancelamentoRegra("SEM_REEMBOLSO_PRAZO");
        em.persist(cancelada);
        em.flush();

        int n = reconciliador.reconciliar();
        assertEquals(2, n);
        assertTrue(conversas.findAll().stream().anyMatch(c -> c.getReservaIds().contains(confirmada.getId())));
        assertEquals(0, reconciliador.reconciliar(), "segunda passada nao encontra mais nada");
    }

    // ------------------------------------------------------ mensagens e filtro

    @Test
    @DisplayName("CT520 - Mensagem com telefone, link e xingamento vai borrada: o original so existe no banco, restrito")
    void filtroNoEnvio() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        String original = "me liga 48 99999-0000, veja www.golpe.com seu idiota";

        EnvioResposta r = chat.enviar(cliente, c.codigo(), original);

        assertNotNull(r.aviso());
        assertTrue(r.aviso().startsWith("Alguns trechos foram ocultados"));
        assertEquals(3, r.mensagem().ocorrencias());
        assertFalse(r.mensagem().texto().contains("99999"));
        assertFalse(r.mensagem().texto().contains("golpe"));
        assertFalse(r.mensagem().texto().contains("idiota"));

        // O destinatario le o mesmo texto filtrado
        List<Mensagem> doGestor = chat.listarMensagens(gestor, c.codigo(), null);
        assertEquals(1, doGestor.size());
        assertEquals(r.mensagem().texto(), doGestor.get(0).texto());
        assertFalse(doGestor.get(0).minha());

        // O original ficou guardado, restrito ao backend
        SmartChatMensagem salva = mensagens.findAll().get(0);
        assertEquals(original, salva.getTextoOriginal());
        assertTrue(salva.getCategorias().contains("TELEFONE") && salva.getCategorias().contains("LINK"));
    }

    @Test
    @DisplayName("CT521 - Mensagem normal (datas, valores, quantidades) passa intacta e sem aviso")
    void mensagemLimpa() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        String texto = "Chegamos dia 10/12/2026 às 14:00, somos 4 pessoas, R$ 980,00 certo? Reserva IMV-000001";
        EnvioResposta r = chat.enviar(cliente, c.codigo(), texto);
        assertNull(r.aviso());
        assertEquals(texto, r.mensagem().texto());
    }

    @Test
    @DisplayName("CT522 - O que sai do servico nunca inclui o texto original: nem em DTO de mensagem, conversa ou perfil")
    void dtosSemOriginalNemContato() {
        for (Class<?> dto : new Class<?>[]{Mensagem.class, Conversa.class, Perfil.class,
                br.com.unisenai.smartrent.dto.SmartChatDtos.Interlocutor.class}) {
            for (var comp : dto.getRecordComponents()) {
                String nome = comp.getName().toLowerCase();
                assertFalse(nome.contains("original") || nome.contains("email") || nome.contains("telefone"),
                        dto.getSimpleName() + "." + comp.getName());
            }
        }
    }

    @Test
    @DisplayName("CT523 - Pre-visualizacao da lista e notificacao usam o texto filtrado")
    void previaENotificacaoFiltradas() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "meu zap é 48 99999-0000");

        Conversa naLista = chat.listar(gestor, null).get(0);
        assertFalse(naLista.ultimaMensagem().contains("9999"));
        assertTrue(naLista.ultimaMensagem().contains("[ocultado]"));
        Notificacao n = em.getEntityManager().createQuery("select n from Notificacao n where n.usuario.id = :g", Notificacao.class)
                .setParameter("g", gestor.getId()).getSingleResult();
        assertFalse(n.getMensagem().contains("9999"));
        assertTrue(n.getTitulo().startsWith("Nova mensagem de Maria"));
    }

    @Test
    @DisplayName("CT524 - Notificacoes de mensagem sao agrupadas: uma por conversa a cada 10 minutos")
    void notificacaoAgrupada() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Oi");
        chat.enviar(cliente, c.codigo(), "Tudo bem?");
        chat.enviar(cliente, c.codigo(), "Alguém aí?");
        assertEquals(1, avisosDoGestor());

        relogio.avancar(Duration.ofMinutes(11));
        chat.enviar(cliente, c.codigo(), "Voltei");
        assertEquals(2, avisosDoGestor());
    }

    private long avisosDoGestor() {
        return em.getEntityManager().createQuery("select count(n) from Notificacao n where n.usuario.id = :g", Long.class)
                .setParameter("g", gestor.getId()).getSingleResult();
    }

    @Test
    @DisplayName("CT525 - Contador de nao lidas por conversa e total; marcar como lida zera; mensagem propria e de sistema nao contam")
    void naoLidas() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Oi");
        chat.enviar(cliente, c.codigo(), "Tudo certo?");
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        chat.garantirConversaDaReserva(r.getId()); // sistema: nao conta

        assertEquals(2, chat.naoLidas(gestor));
        assertEquals(2, chat.listar(gestor, null).get(0).naoLidas());
        assertEquals(0, chat.naoLidas(cliente), "a propria mensagem nao conta");

        chat.marcarLidas(gestor, c.codigo());
        assertEquals(0, chat.naoLidas(gestor));
        assertTrue(chat.listarMensagens(cliente, c.codigo(), null).stream().filter(m -> m.minha()).allMatch(Mensagem::lida));
    }

    @Test
    @DisplayName("CT526 - Lista ordenada pela ultima mensagem e filtravel por imovel; so as conversas do proprio usuario")
    void listaOrdenadaEIsolada() {
        Imovel segundo = novoImovel(gestor, StatusAnuncio.PUBLICADO);
        Imovel doOutro = novoImovel(outroGestor, StatusAnuncio.PUBLICADO);
        Conversa a = chat.abrirPorImovel(cliente, imovel.getId());
        Conversa b = chat.abrirPorImovel(cliente, segundo.getId());
        Conversa deOutro = chat.abrirPorImovel(outroCliente, doOutro.getId());
        relogio.avancar(Duration.ofMinutes(1));
        chat.enviar(cliente, a.codigo(), "primeira");
        relogio.avancar(Duration.ofMinutes(1));
        chat.enviar(cliente, b.codigo(), "segunda");

        List<Conversa> doGestor = chat.listar(gestor, null);
        assertEquals(List.of(b.codigo(), a.codigo()), doGestor.stream().map(Conversa::codigo).toList(), "mais recente primeiro");
        assertEquals(1, chat.listar(gestor, imovel.getId()).size());
        assertTrue(doGestor.stream().noneMatch(x -> x.codigo().equals(deOutro.codigo())));
        assertEquals(2, chat.listar(cliente, null).size());
        assertEquals(1, chat.listar(outroGestor, null).size());
    }

    @Test
    @DisplayName("CT527 - Quem nao participa nao le, nao envia, nao marca como lida nem ve o perfil: recebe o mesmo 404 de conversa inexistente")
    void isolamento() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        for (Usuario intruso : List.of(outroCliente, outroGestor)) {
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.buscar(intruso, c.codigo()));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.listarMensagens(intruso, c.codigo(), null));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.enviar(intruso, c.codigo(), "oi"));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.marcarLidas(intruso, c.codigo()));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.perfil(intruso, c.codigo()));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.denunciar(intruso, c.codigo(), new DenunciaPedido("SPAM", null, null)));
            assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, () -> chat.bloquear(intruso, c.codigo()));
        }
    }

    @Test
    @DisplayName("CT528 - Mensagem vazia, longa demais e excesso de envios (rate limit) sao recusados")
    void validacoesELimite() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        assertThrows(IllegalArgumentException.class, () -> chat.enviar(cliente, c.codigo(), "   "));
        assertThrows(IllegalArgumentException.class, () -> chat.enviar(cliente, c.codigo(), "x".repeat(1001)));

        for (int i = 0; i < 20; i++) {
            chat.enviar(cliente, c.codigo(), "mensagem " + i);
        }
        assertThrows(LimiteExcedidoException.class, () -> chat.enviar(cliente, c.codigo(), "mais uma"));
        relogio.avancar(Duration.ofMinutes(2));
        assertDoesNotThrow(() -> chat.enviar(cliente, c.codigo(), "agora pode"));
    }

    @Test
    @DisplayName("CT529 - Primeira mensagem do cliente tambem libera a aba; o gestor nao precisa de flag")
    void primeiraMensagemLiberaAba() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        cliente.setSmartchatLiberado(false); // simula conversa criada por outro caminho
        em.persist(cliente);
        chat.enviar(cliente, c.codigo(), "Oi!");
        assertTrue(cliente.isSmartchatLiberado());
        assertFalse(gestor.isSmartchatLiberado());
    }

    // ------------------------------------------------ denunciar e bloquear

    @Test
    @DisplayName("UUID - A mensagem sai com codigo publico (sem id sequencial) e o cursor depoisDe aceita so codigos da propria conversa")
    void mensagemComCodigoPublicoECursor() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        EnvioResposta m1 = chat.enviar(cliente, c.codigo(), "primeira");
        EnvioResposta m2 = chat.enviar(cliente, c.codigo(), "segunda");
        EnvioResposta m3 = chat.enviar(cliente, c.codigo(), "terceira");

        assertNotEquals(m1.mensagem().codigo(), m2.mensagem().codigo());
        assertFalse(m1.mensagem().toString().matches("(?s).*\bid=.*"), "o DTO nao expoe id numerico");
        assertTrue(java.util.Arrays.stream(Mensagem.class.getRecordComponents()).noneMatch(rc -> rc.getName().equals("id")));

        List<Mensagem> aposPrimeira = chat.listarMensagens(gestor, c.codigo(), m1.mensagem().codigo());
        assertEquals(List.of(m2.mensagem().codigo(), m3.mensagem().codigo()),
                aposPrimeira.stream().map(Mensagem::codigo).toList());
        assertEquals(3, chat.listarMensagens(gestor, c.codigo(), null).size());

        // codigo de mensagem de OUTRA conversa nao serve de cursor (e nao revela que existe): volta ao inicio
        Imovel outro = imovel;
        Conversa deOutroCliente = chat.abrirPorImovel(outroCliente, outro.getId());
        EnvioResposta alheia = chat.enviar(outroCliente, deOutroCliente.codigo(), "da outra conversa");
        assertEquals(3, chat.listarMensagens(gestor, c.codigo(), alheia.mensagem().codigo()).size());
        assertEquals(3, chat.listarMensagens(gestor, c.codigo(), java.util.UUID.randomUUID()).size());
    }

    @Test
    @DisplayName("UUID - Denuncia ignora codigos de mensagens de outra conversa")
    void denunciaSoAnexaMensagensDaPropriaConversa() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        EnvioResposta minha = chat.enviar(cliente, c.codigo(), "ola");
        Conversa outra = chat.abrirPorImovel(outroCliente, imovel.getId());
        EnvioResposta alheia = chat.enviar(outroCliente, outra.codigo(), "de outra pessoa");

        chat.denunciar(gestor, c.codigo(), new DenunciaPedido("SPAM", null, List.of(minha.mensagem().codigo(), alheia.mensagem().codigo())));

        long idMinha = mensagens.findAll().stream().filter(m -> m.getCodigoPublico().equals(minha.mensagem().codigo())).findFirst().orElseThrow().getId();
        assertEquals(String.valueOf(idMinha), denuncias.findAll().get(0).getMensagensAnexadas());
    }

    @Test
    @DisplayName("CT530 - Denuncia persiste com os dados corretos e PENDENTE; nao aparece para o denunciado")
    void denunciaPersiste() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Oi");
        long idMsg = mensagens.findAll().get(0).getId();
        java.util.UUID codigoMsg = mensagens.findAll().get(0).getCodigoPublico();

        var conf = chat.denunciar(gestor, c.codigo(), new DenunciaPedido("TENTATIVA_DE_GOLPE", "pediu pix fora", List.of(codigoMsg, java.util.UUID.randomUUID())));

        assertEquals("Denúncia registrada.", conf.mensagem());
        var d = denuncias.findAll().get(0);
        assertEquals("PENDENTE", d.getStatus());
        assertEquals(gestor.getId(), d.getDenunciante().getId());
        assertEquals(cliente.getId(), d.getDenunciado().getId());
        assertEquals("TENTATIVA_DE_GOLPE", d.getMotivo());
        assertEquals("pediu pix fora", d.getDescricao());
        assertEquals(String.valueOf(idMsg), d.getMensagensAnexadas(), "so mensagens da propria conversa sao anexadas");
        assertEquals(c.codigo(), d.getConversa().getCodigoPublico());
        // o denunciado continua usando o chat normalmente e nada vaza para ele
        assertEquals(1, chat.listarMensagens(cliente, c.codigo(), null).size());
    }

    @Test
    @DisplayName("CT531 - Denuncia exige motivo valido; duplicata identica em curto intervalo e barrada")
    void denunciaValidacoes() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        assertThrows(IllegalArgumentException.class, () -> chat.denunciar(gestor, c.codigo(), new DenunciaPedido("INVENTADO", null, null)));
        assertThrows(IllegalArgumentException.class, () -> chat.denunciar(gestor, c.codigo(), new DenunciaPedido(null, null, null)));

        chat.denunciar(gestor, c.codigo(), new DenunciaPedido("SPAM", null, null));
        assertThrows(LimiteExcedidoException.class, () -> chat.denunciar(gestor, c.codigo(), new DenunciaPedido("SPAM", null, null)));
        assertDoesNotThrow(() -> chat.denunciar(gestor, c.codigo(), new DenunciaPedido("OUTRO", null, null)), "outro motivo e outra denuncia");
        relogio.avancar(Duration.ofMinutes(6));
        assertDoesNotThrow(() -> chat.denunciar(gestor, c.codigo(), new DenunciaPedido("SPAM", null, null)));
    }

    @Test
    @DisplayName("CT532 - Bloqueio e persistido, sem efeito funcional com a flag desligada; texto nao afirma efeito inexistente")
    void bloqueioSemEfeito() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());

        var conf = chat.bloquear(cliente, c.codigo());

        assertEquals("Sua solicitação de bloqueio foi registrada.", conf.mensagem());
        assertFalse(conf.mensagem().toLowerCase().contains("bloqueado"));
        assertFalse(conf.mensagem().toLowerCase().contains("não receberá"));
        var b = bloqueios.findAll().get(0);
        assertEquals(cliente.getId(), b.getBloqueador().getId());
        assertEquals(gestor.getId(), b.getBloqueado().getId());
        assertEquals("REGISTRADO", b.getStatus());
        assertFalse(ChatProperties.padrao().bloqueioAtivo(), "padrao SMARTCHAT_BLOCK_ENFORCEMENT=false");
        // a conversa segue funcionando
        assertDoesNotThrow(() -> chat.enviar(gestor, c.codigo(), "ainda posso responder"));
        assertDoesNotThrow(() -> chat.enviar(cliente, c.codigo(), "e eu também"));
    }

    @Test
    @DisplayName("CT533 - Perfil da conversa mostra nome, imovel e reserva; nunca e-mail ou telefone")
    void perfilSemContato() {
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        chat.garantirConversaDaReserva(r.getId());
        java.util.UUID id = conversas.findAll().get(0).getCodigoPublico();

        Perfil doGestor = chat.perfil(gestor, id);
        assertEquals("Maria Souza", doGestor.interlocutor().nome());
        assertEquals("Cliente", doGestor.interlocutor().papel());
        assertEquals("MS", doGestor.interlocutor().iniciais());
        assertEquals("Gestor", chat.perfil(cliente, id).interlocutor().papel());
        String json = doGestor.toString() + chat.perfil(cliente, id);
        assertFalse(json.contains("@"), json);
    }

    @Test
    @DisplayName("Cifra em repouso - o banco guarda so texto cifrado e a leitura devolve o texto; sem mudar a API")
    void textoFicaCifradoNoBanco() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        EnvioResposta r = chat.enviar(cliente, c.codigo(), "Olá! Posso levar meu cachorro? 🐶 Ligue 48 99999-0000");
        em.flush();

        String filtradoNoBanco = jdbc.queryForObject("select texto_filtrado from smartchat_mensagens where codigo_publico = ?",
                String.class, r.mensagem().codigo());
        String originalNoBanco = jdbc.queryForObject("select texto_original from smartchat_mensagens where codigo_publico = ?",
                String.class, r.mensagem().codigo());
        assertTrue(filtradoNoBanco.startsWith("v1:") && !filtradoNoBanco.contains("cachorro"));
        assertTrue(originalNoBanco.startsWith("v1:") && !originalNoBanco.contains("99999"));

        em.clear();
        SmartChatMensagem lida = mensagens.findAll().stream().filter(m -> m.getCodigoPublico().equals(r.mensagem().codigo())).findFirst().orElseThrow();
        assertTrue(lida.getTextoFiltrado().contains("cachorro"));
        assertEquals("Olá! Posso levar meu cachorro? 🐶 Ligue 48 99999-0000", lida.getTextoOriginal());
        assertTrue(chat.listarMensagens(gestor, c.codigo(), null).stream().anyMatch(m -> m.texto().contains("cachorro")));

        chat.denunciar(cliente, c.codigo(), new DenunciaPedido("ASSEDIO_OFENSAS", "ele pediu o meu telefone", List.of(r.mensagem().codigo())));
        em.flush();
        assertTrue(jdbc.queryForObject("select descricao from smartchat_denuncias", String.class).startsWith("v1:"));
        em.clear();
        assertEquals("ele pediu o meu telefone", denuncias.findAll().get(0).getDescricao());
    }

    @Test
    @DisplayName("Fraude - so o destinatario ve o alerta; o texto nao e alterado e o autor nao e avisado")
    void alertaDeFraudeSoParaODestinatario() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        EnvioResposta r = chat.enviar(cliente, c.codigo(), "Podemos fazer o pagamento por fora? Eu faço um pix direto");
        assertEquals("Podemos fazer o pagamento por fora? Eu faço um pix direto", r.mensagem().texto());
        assertEquals(0, r.mensagem().ocorrencias());
        assertNull(r.aviso(), "nao e bloqueio nem mascaramento");
        assertFalse(r.mensagem().suspeitaFraude(), "o autor nao ve o alerta");

        List<Mensagem> doGestor = chat.listarMensagens(gestor, c.codigo(), null);
        assertTrue(doGestor.get(0).suspeitaFraude(), "o destinatario ve");
        assertFalse(chat.listarMensagens(cliente, c.codigo(), null).get(0).suspeitaFraude());

        chat.enviar(cliente, c.codigo(), "Qual o horário do check-in?");
        assertFalse(chat.listarMensagens(gestor, c.codigo(), null).get(1).suspeitaFraude());
    }

    // ------------------------------------------------- analise de comportamento

    @Autowired private br.com.unisenai.smartrent.repository.AlertaInternoRepository alertasInternos;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void contaAntiga(Usuario u) {
        em.flush();
        jdbcTemplate.update("update usuarios set data_criacao = ? where id = ?", java.sql.Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 0, 0)), u.getId());
        u.setDataCriacao(LocalDateTime.of(2025, 1, 1, 0, 0)); // a entidade gerenciada segue o banco (coluna nao atualizavel pelo JPA)
    }

    /** Uma conversa por imovel, para o mesmo cliente falar com varios gestores. */
    private List<java.util.UUID> conversasDoCliente(Usuario autor, int quantas) {
        java.util.ArrayList<java.util.UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            Imovel outro = novoImovel(gestor, StatusAnuncio.PUBLICADO);
            em.flush();
            ids.add(chat.abrirPorImovel(autor, outro.getId()).codigo());
        }
        return ids;
    }

    private long alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno tipo) {
        return alertasInternos.findAll().stream().filter(a -> a.getTipo() == tipo).count();
    }

    @Test
    @DisplayName("Envio em massa - conta antiga: abaixo de N nao dispara, ao atingir N dispara um unico alerta")
    void envioEmMassaContaAntiga() {
        contaAntiga(cliente);
        List<java.util.UUID> ids = conversasDoCliente(cliente, 6);
        for (int i = 0; i < 4; i++) {
            chat.enviar(cliente, ids.get(i), "Olá! Tenho um imóvel incrível para você, me chame para saber mais.");
        }
        assertEquals(0, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA), "4 < 5");
        chat.enviar(cliente, ids.get(4), "Olá! Tenho um imóvel incrível para você, me chame para saber mais.");
        assertEquals(1, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));
        relogio.avancar(Duration.ofSeconds(61)); // o limite restrito (3/min) ja esta valendo: espera a janela de 1 min
        chat.enviar(cliente, ids.get(5), "olá tenho um imovel incrivel para voce me chame para saber mais");
        assertEquals(1, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA), "nao duplica na janela");

        br.com.unisenai.smartrent.model.AlertaInterno a = alertasInternos.findAll().get(0);
        assertEquals(cliente.getId(), a.getUsuario().getId());
        assertEquals(5, a.getContagem());
        assertEquals(br.com.unisenai.smartrent.model.enums.StatusAlertaInterno.ABERTO, a.getStatus());
        assertTrue(a.getJanelaInicio().isBefore(a.getJanelaFim()));
    }

    @Test
    @DisplayName("Envio em massa - conta nova pesa mais: o limiar e menor")
    void envioEmMassaContaNova() {
        List<java.util.UUID> ids = conversasDoCliente(cliente, 3); // conta criada agora: limiar 3
        chat.enviar(cliente, ids.get(0), "Fale comigo fora daqui, tenho uma oferta imperdível.");
        chat.enviar(cliente, ids.get(1), "Fale comigo fora daqui, tenho uma oferta imperdível.");
        assertEquals(0, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));
        chat.enviar(cliente, ids.get(2), "Fale comigo fora daqui, tenho uma oferta imperdível.");
        assertEquals(1, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));
    }

    @Test
    @DisplayName("Envio em massa - textos diferentes nao disparam; texto repetido na MESMA conversa tambem nao")
    void textosDiferentesNaoDisparam() {
        contaAntiga(cliente);
        List<java.util.UUID> ids = conversasDoCliente(cliente, 6);
        for (int i = 0; i < 6; i++) {
            chat.enviar(cliente, ids.get(i), "Mensagem numero " + i + " sobre a reserva do apartamento.");
        }
        assertEquals(0, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));

        for (int i = 0; i < 6; i++) {
            chat.enviar(cliente, ids.get(0), "Chegarei por volta das quinze horas, tudo certo?");
        }
        assertEquals(0, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA), "conversas distintas e que contam");
    }

    @Test
    @DisplayName("Envio em massa - so o HMAC do texto normalizado e guardado, nunca o texto; textos curtos nao entram")
    void guardaSoHmac() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        EnvioResposta r = chat.enviar(cliente, c.codigo(), "Olá, quero saber sobre o apartamento!");
        chat.enviar(cliente, c.codigo(), "ok");
        em.flush();
        List<String> hmacs = jdbcTemplate.queryForList("select texto_hmac from smartchat_mensagens order by id", String.class);
        assertEquals(64, hmacs.get(0).length());
        assertTrue(hmacs.get(0).matches("[0-9a-f]{64}"));
        assertFalse(hmacs.get(0).contains("apartamento"));
        assertNull(hmacs.get(1), "texto curto demais para comparar");
        assertNotNull(r);
    }

    @Test
    @DisplayName("Envio em massa - depois do alerta o limite de envio fica restrito; a conta nao e suspensa")
    void limiteRestritoSemSuspender() {
        List<java.util.UUID> ids = conversasDoCliente(cliente, 3);
        for (java.util.UUID id : ids) {
            chat.enviar(cliente, id, "Fale comigo fora daqui, tenho uma oferta imperdível.");
        }
        assertEquals(1, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));
        // limite restrito = 3 por minuto: ja foram 3 na janela
        assertThrows(LimiteExcedidoException.class, () -> chat.enviar(cliente, ids.get(0), "Mais uma mensagem qualquer aqui."));
        assertTrue(em.find(Usuario.class, cliente.getId()).isAtivo(), "nao suspende a conta");
        // passado o tempo do limite restrito (60 min) volta ao normal
        relogio.avancar(Duration.ofMinutes(61));
        assertDoesNotThrow(() -> chat.enviar(cliente, ids.get(0), "Agora posso escrever de novo normalmente."));
    }

    @Test
    @DisplayName("Fraude - gera um alerta interno por usuario na janela, sem conteudo, e a mensagem segue normal")
    void alertaInternoDeFraude() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Podemos pagar por fora? Faço um pix direto.");
        chat.enviar(cliente, c.codigo(), "Me passa a chave pix então.");
        assertEquals(1, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.SUSPEITA_FRAUDE));
        assertEquals(1, alertasInternos.findAll().get(0).getContagem());
        assertEquals(0, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.ENVIO_EM_MASSA));
        relogio.avancar(Duration.ofMinutes(61));
        chat.enviar(cliente, c.codigo(), "Aceita deposito antecipado?");
        assertEquals(2, alertas(br.com.unisenai.smartrent.model.enums.TipoAlertaInterno.SUSPEITA_FRAUDE), "novo alerta so depois da janela");
    }

    // --------------------------------------------------- e-mail verificado

    @Test
    @DisplayName("E-mail nao verificado - le as conversas, mas nao envia mensagem nem abre conversa nova (regra no backend)")
    void semEmailVerificadoSoLe() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Mensagem enviada com o e-mail verificado.");
        chat.enviar(gestor, c.codigo(), "Resposta do gestor.");

        cliente.setEmailVerificadoEm(null);
        assertThrows(br.com.unisenai.smartrent.service.erro.EmailNaoVerificadoException.class,
                () -> chat.enviar(cliente, c.codigo(), "Tentando enviar sem verificar."));
        assertEquals(2, chat.listarMensagens(cliente, c.codigo(), null).size(), "a mensagem recusada nao foi gravada");
        assertEquals(1, chat.listar(cliente, null).size(), "a lista continua");
        assertEquals(c.codigo(), chat.abrirPorImovel(cliente, imovel.getId()).codigo(), "conversa existente abre (leitura)");

        Imovel outro = novoImovel(gestor, StatusAnuncio.PUBLICADO);
        em.flush();
        assertThrows(br.com.unisenai.smartrent.service.erro.EmailNaoVerificadoException.class,
                () -> chat.abrirPorImovel(cliente, outro.getId()), "conversa nova exige verificacao");
        Reserva r = reserva(cliente, outro, StatusReserva.CONFIRMADA, 20);
        assertThrows(br.com.unisenai.smartrent.service.erro.EmailNaoVerificadoException.class,
                () -> chat.abrirPorReserva(cliente, r.getId()));
        assertEquals(1, conversas.count(), "nenhuma conversa nova foi criada");

        cliente.setEmailVerificadoEm(relogio.instant());
        assertDoesNotThrow(() -> chat.enviar(cliente, c.codigo(), "Agora sim, e-mail verificado."));
    }

    @Test
    @DisplayName("E-mail nao verificado - a mensagem de sistema e a criacao automatica da conversa pela reserva continuam funcionando")
    void sistemaContinuaSemVerificacao() {
        cliente.setEmailVerificadoEm(null);
        Reserva r = reserva(cliente, imovel, StatusReserva.CONFIRMADA, 0);
        em.flush();
        chat.garantirConversaDaReserva(r.getId());
        assertEquals(1, conversas.count());
        List<Mensagem> msgs = chat.listarMensagens(cliente, conversas.findAll().get(0).getCodigoPublico(), null);
        assertEquals(1, msgs.size());
        assertEquals("SISTEMA", msgs.get(0).tipo());
    }

    @Test
    @DisplayName("Perfil da conversa - mostra se o e-mail do interlocutor esta verificado (selo), sem falar em identidade")
    void perfilMostraSeloDeEmail() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        assertTrue(chat.perfil(cliente, c.codigo()).interlocutor().verificado());
        gestor.setEmailVerificadoEm(null);
        assertFalse(chat.perfil(cliente, c.codigo()).interlocutor().verificado());
        assertTrue(chat.perfil(gestor, c.codigo()).interlocutor().verificado(), "o cliente continua verificado");
        assertFalse(chat.perfil(cliente, c.codigo()).toString().toLowerCase().contains("identidade"));
    }

    // ------------------------------------------------ UUID no lugar do id sequencial

    @org.springframework.boot.test.mock.mockito.SpyBean private ChatEventos eventosEspionado;

    @Test
    @DisplayName("UUID - conversa de terceiro e conversa inexistente dao exatamente a mesma resposta (404, mesma mensagem) em toda operacao")
    void mesmoNaoEncontradoParaTerceiroEInexistente() {
        Conversa minha = chat.abrirPorImovel(cliente, imovel.getId());
        java.util.UUID inexistente = java.util.UUID.randomUUID();
        Usuario intruso = outroCliente;

        java.util.List<org.junit.jupiter.api.function.Executable> deTerceiro = java.util.List.of(
                () -> chat.buscar(intruso, minha.codigo()),
                () -> chat.listarMensagens(intruso, minha.codigo(), null),
                () -> chat.enviar(intruso, minha.codigo(), "invasao"),
                () -> chat.perfil(intruso, minha.codigo()),
                () -> chat.marcarLidas(intruso, minha.codigo()),
                () -> chat.denunciar(intruso, minha.codigo(), new DenunciaPedido("SPAM", null, List.of())),
                () -> chat.bloquear(intruso, minha.codigo()));
        java.util.List<org.junit.jupiter.api.function.Executable> deInexistente = java.util.List.of(
                () -> chat.buscar(intruso, inexistente),
                () -> chat.listarMensagens(intruso, inexistente, null),
                () -> chat.enviar(intruso, inexistente, "invasao"),
                () -> chat.perfil(intruso, inexistente),
                () -> chat.marcarLidas(intruso, inexistente),
                () -> chat.denunciar(intruso, inexistente, new DenunciaPedido("SPAM", null, List.of())),
                () -> chat.bloquear(intruso, inexistente));
        for (int k = 0; k < deTerceiro.size(); k++) {
            var a = assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, deTerceiro.get(k));
            var b = assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class, deInexistente.get(k));
            assertEquals(b.getMessage(), a.getMessage(), "operacao " + k);
            assertEquals(b.getClass(), a.getClass());
        }
        assertEquals(0, chat.listarMensagens(cliente, minha.codigo(), null).size(), "a invasao nao gravou nada");
    }

    @Test
    @DisplayName("UUID - o controlador trata id numerico antigo e UUID malformado como conversa inexistente (mesmo 404)")
    void controladorTrataIdMalformadoComoInexistente() {
        br.com.unisenai.smartrent.controller.SmartChatController ctrl =
                new br.com.unisenai.smartrent.controller.SmartChatController(chat, eventosEspionado);
        Conversa minha = chat.abrirPorImovel(cliente, imovel.getId());
        String mensagem = assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class,
                () -> ctrl.buscar(outroCliente, java.util.UUID.randomUUID().toString())).getMessage();
        for (String malformado : new String[]{"1", String.valueOf(conversas.findAll().get(0).getId()), "nao-e-uuid", "", " "}) {
            var e = assertThrows(br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException.class,
                    () -> ctrl.buscar(cliente, malformado), malformado);
            assertEquals(mensagem, e.getMessage());
        }
        assertEquals(minha.codigo(), ctrl.buscar(cliente, minha.codigo().toString()).codigo(), "o UUID certo funciona");
    }

    @Test
    @DisplayName("UUID - nenhum DTO do chat expoe id numerico de conversa ou de usuario")
    void dtosSemIdNumericoDeConversaOuUsuario() {
        // Conversa: so o UUID. Nenhum componente numerico "id" (de conversa) e nenhum id de usuario em lugar nenhum.
        var componentesConversa = java.util.Arrays.stream(Conversa.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertTrue(componentesConversa.contains("codigo"));
        assertFalse(componentesConversa.contains("id"));
        assertEquals(java.util.UUID.class, java.util.Arrays.stream(Conversa.class.getRecordComponents())
                .filter(c -> c.getName().equals("codigo")).findFirst().orElseThrow().getType());
        java.util.regex.Pattern idDeUsuario = java.util.regex.Pattern.compile("(?i)(usuario|autor|cliente|gestor|denunciante|denunciado|interlocutor)_?id");
        for (Class<?> dto : br.com.unisenai.smartrent.dto.SmartChatDtos.class.getDeclaredClasses()) {
            if (!dto.isRecord()) {
                continue;
            }
            for (var comp : dto.getRecordComponents()) {
                assertFalse(idDeUsuario.matcher(comp.getName()).find(), dto.getSimpleName() + "." + comp.getName());
                boolean numerico = Number.class.isAssignableFrom(comp.getType()) || comp.getType() == long.class;
                boolean topoDaConversa = dto == Conversa.class || dto == Perfil.class
                        || dto == br.com.unisenai.smartrent.dto.SmartChatDtos.Interlocutor.class;
                assertFalse(topoDaConversa && numerico && comp.getName().toLowerCase().endsWith("id"), dto.getSimpleName() + "." + comp.getName());
            }
        }
        // E o JSON de verdade: o id sequencial da conversa nao aparece em lugar nenhum
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Oi, tudo bem com a reserva?");
        String json = c.toString() + chat.listar(cliente, null) + chat.listarMensagens(gestor, c.codigo(), null) + chat.perfil(gestor, c.codigo());
        assertTrue(json.contains(c.codigo().toString()));
        assertFalse(json.contains("id=" + cliente.getId() + ",") || json.contains("usuarioId"), json);
    }

    @Test
    @DisplayName("UUID - o evento SSE e o link da notificacao levam so o UUID: nenhum id numerico")
    void eventosELinksSoComUuid() {
        Conversa c = chat.abrirPorImovel(cliente, imovel.getId());
        chat.enviar(cliente, c.codigo(), "Chegamos amanhã às 14h, tudo certo?");
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> dados = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(eventosEspionado, org.mockito.Mockito.atLeastOnce())
                .publicar(org.mockito.ArgumentMatchers.eq(gestor.getId()), org.mockito.ArgumentMatchers.eq("mensagem"), dados.capture());
        java.util.Map<String, Object> evento = dados.getValue();
        assertEquals(java.util.Set.of("conversa"), evento.keySet(), "sem mensagemId nem conversaId numericos");
        assertEquals(c.codigo().toString(), evento.get("conversa"));

        // ticket do SSE: valor aleatorio (UUID), nunca o id do usuario
        String ticket = eventosEspionado.emitirTicket(cliente.getId());
        assertDoesNotThrow(() -> java.util.UUID.fromString(ticket));
        assertNotEquals(String.valueOf(cliente.getId()), ticket);

        // link da notificacao
        String link = jdbcTemplate.queryForObject("select link from notificacoes where usuario_id = ? order by id desc limit 1",
                String.class, gestor.getId());
        assertEquals("/smartchat.html?conversa=" + c.codigo(), link);
    }
}
