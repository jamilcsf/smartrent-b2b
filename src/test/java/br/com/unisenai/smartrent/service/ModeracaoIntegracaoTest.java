package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisaoDenunciaRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MensagemAdminRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.RevisaoAlertaRequest;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Conversa;
import br.com.unisenai.smartrent.dto.SmartChatDtos.DenunciaPedido;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AlertaInternoRepository;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Moderacao e painel de admin com banco de verdade (H2) e o SmartChat real: o filtro gera dados de verdade, o cliente denuncia
 * e o admin decide. Cobre as consultas de agregacao (por dia, por categoria) e as listagens com filtro.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderacaoint;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({SmartChatService.class, MessageFilterService.class, ChatEventos.class, LimitadorDeTaxa.class,
        NotificacaoService.class, NotificadorEmailLog.class, TextosPoliticas.class, ChatReconciliador.class,
        AnaliseComportamentoChat.class, ModeracaoService.class, AuditoriaContaService.class, EmailSenderLog.class,
        ModeracaoIntegracaoTest.Config.class})
class ModeracaoIntegracaoTest {

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
    @Autowired private ModeracaoService moderacao;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ImovelRepository imoveis;
    @Autowired private ReservaRepository reservas;
    @Autowired private DenunciaChatRepository denuncias;
    @Autowired private AlertaInternoRepository alertas;

    private Usuario admin;
    private Usuario gestor;
    private Usuario cliente;
    private Imovel imovel;
    private Conversa conversa;

    private Usuario usuario(String nome, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(papel);
        u.setAtivo(true);
        u.setEmailVerificadoEm(relogio.instant());
        return em.persist(u);
    }

    @BeforeEach
    void preparar() {
        admin = usuario("Admin Geral", PapelUsuario.ADMIN);
        gestor = usuario("Gestor Um", PapelUsuario.ANFITRIAO);
        cliente = usuario("Maria Souza", PapelUsuario.CLIENTE);
        Imovel i = new Imovel();
        i.setUsuario(gestor);
        i.setTitulo("Apto Canasvieiras");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(4);
        i.setNumeroQuartos(2);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setStatus(StatusAnuncio.PUBLICADO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        imovel = em.persist(i);
        em.flush();
        conversa = chat.abrirPorImovel(cliente, imovel.getId());
    }

    private java.util.UUID enviar(Usuario autor, String texto) {
        return chat.enviar(autor, conversa.codigo(), texto).mensagem().codigo();
    }

    private Long denunciaComEvidencias() {
        java.util.UUID m1 = enviar(gestor, "Me chama no zap 48 99999-1234 ou jose@exemplo.com");
        java.util.UUID m2 = enviar(gestor, "Faz o pagamento por pix direto para mim, fora da plataforma, que eu confirmo.");
        chat.denunciar(cliente, conversa.codigo(), new DenunciaPedido("TENTATIVA_DE_GOLPE", "Pediu pix por fora <b>agora</b>", List.of(m1, m2)));
        return denuncias.findAll().get(0).getId();
    }

    private List<Imovel> visiveis() {
        return imoveis.findVisiveis(LocalDateTime.of(2026, 10, 3, 15, 0));
    }

    @Test
    @DisplayName("CT1147 - Denuncia de verdade: lista pendentes, detalhe com evidencias ocultadas, alerta do filtro e abertura registrada")
    void detalheDaDenuncia() {
        Long id = denunciaComEvidencias();
        var lista = moderacao.denuncias("pendente", 0, 15);
        assertEquals(1, lista.total());
        assertEquals("TENTATIVA_DE_GOLPE", lista.itens().get(0).motivo());
        assertFalse(lista.itens().get(0).descricao().contains("<b>"), "descricao sem HTML");
        assertEquals(0, moderacao.denuncias("PROCEDENTE", 0, 15).total());
        assertEquals(1, moderacao.denuncias(null, 0, 15).total());

        var d = moderacao.denuncia(admin, id);
        assertEquals("Apto Canasvieiras", d.imovel());
        assertEquals(2, d.evidencias().size());
        String textos = d.evidencias().get(0).texto() + d.evidencias().get(1).texto();
        assertFalse(textos.contains("99999"), "telefone continua oculto para o admin");
        assertFalse(textos.contains("jose@exemplo.com"));
        assertTrue(d.alertasDoDenunciado().stream().anyMatch(a -> a.tipo().equals("SUSPEITA_FRAUDE")), "o filtro abriu o alerta sozinho");
        assertNull(d.decisao());
        // abrir o detalhe ficou registrado na trilha
        assertEquals(1, moderacao.historico(gestor.getId(), 0, 10).itens().stream().filter(a -> a.tipo().equals("DENUNCIA_ABERTA")).count());
    }

    @Test
    @DisplayName("CT1148 - Decisoes automaticas de verdade: so contagens por categoria, por dia e por autor; alerta revisado some do filtro de abertos")
    void decisoesAutomaticas() {
        denunciaComEvidencias();
        enviar(cliente, "Obrigada, vou pensar com calma.");
        var r = moderacao.decisoesAutomaticas(null, 30, 0, 10);
        assertEquals(1, r.alertasAbertos());
        assertEquals(3, r.filtro().mensagens());
        assertEquals(2, r.filtro().comAcaoDoFiltro());
        assertEquals(30, r.filtro().dias());
        assertTrue(r.filtro().porCategoria().stream().anyMatch(c -> c.rotulo().startsWith("Telefone") && c.total() == 1));
        assertEquals(1, r.filtro().porDia().size());
        assertEquals("Gestor Um", r.filtro().autoresMaisFiltrados().get(0).nome());
        assertEquals(2, r.filtro().autoresMaisFiltrados().get(0).total());
        assertEquals(1, r.alertas().size());
        assertThrows(IllegalArgumentException.class, () -> moderacao.decisoesAutomaticas("QUALQUER", 30, 0, 10));

        Long idAlerta = alertas.findAll().get(0).getId();
        var revisado = moderacao.revisarAlerta(admin, idAlerta, new RevisaoAlertaRequest("revisado", "Confirmado pela moderacao"));
        assertEquals("REVISADO", revisado.status());
        assertEquals("Confirmado pela moderacao", revisado.revisao().nota());
        assertEquals(0, moderacao.decisoesAutomaticas("ABERTO", 30, 0, 10).alertas().size());
        assertEquals(1, moderacao.decisoesAutomaticas("REVISADO", 7, 0, 10).alertas().size());
    }

    @Test
    @DisplayName("CT1149 - Denuncia procedente com suspensao: gestor inativo, anuncio some do catalogo, conta aparece em suspensas; reativar devolve tudo")
    void suspensaoTiraAnuncioDoCatalogo() {
        Long id = denunciaComEvidencias();
        assertEquals(1, visiveis().size());

        var item = moderacao.decidirDenuncia(admin, id, new DecisaoDenunciaRequest("PROCEDENTE", "Pediu pix por fora", true, true, true), "1.2.3.4");
        assertEquals("PROCEDENTE", item.status());
        assertFalse(usuarios.findById(gestor.getId()).orElseThrow().isAtivo());
        assertEquals(0, visiveis().size(), "anuncio de gestor suspenso sai do catalogo");

        var suspensas = moderacao.contasSuspensas(0, 10);
        assertEquals(1, suspensas.total());
        assertTrue(suspensas.itens().get(0).motivo().contains("Pediu pix por fora"));
        assertEquals("Admin Geral", suspensas.itens().get(0).por());

        var detalhe = moderacao.denuncia(admin, id);
        assertEquals("DENUNCIA_PROCEDENTE", detalhe.decisao().tipo());
        assertEquals(1, detalhe.denunciasProcedentesContra());

        moderacao.reativar(admin, gestor.getId(), "Contestacao aceita", "1.2.3.4");
        assertTrue(usuarios.findById(gestor.getId()).orElseThrow().isAtivo());
        assertEquals(1, visiveis().size());
        assertEquals(0, moderacao.contasSuspensas(0, 10).total());
        var historico = moderacao.historico(gestor.getId(), 0, 20).itens().stream().map(a -> a.tipo()).toList();
        assertTrue(historico.containsAll(List.of("SUSPENSAO", "REATIVACAO", "DENUNCIA_PROCEDENTE")));
        assertTrue(moderacao.historico(null, 0, 20).total() >= historico.size());
    }

    @Test
    @DisplayName("CT1150 - Avisos de verdade: a decisao entrega aviso ao denunciante e ao denunciado e a mensagem direta aparece em Avisos enviados")
    void avisos() {
        Long id = denunciaComEvidencias();
        moderacao.decidirDenuncia(admin, id, new DecisaoDenunciaRequest("PROCEDENTE", "Pediu pix por fora", false, true, true), "ip");
        moderacao.enviarMensagem(admin, cliente.getId(), new MensagemAdminRequest("SUPORTE", "Obrigado", "Agradecemos o seu aviso.", false));
        var enviados = moderacao.comunicadosEnviados(0, 10);
        assertEquals(3, enviados.total());
        assertTrue(enviados.itens().stream().anyMatch(c -> c.assunto().equals("Sua denúncia foi analisada") && c.destinatario().id().equals(cliente.getId())));
        assertTrue(enviados.itens().stream().anyMatch(c -> c.destinatario().id().equals(gestor.getId())));
        assertEquals(4, moderacao.niveis().size());
    }

    @Test
    @DisplayName("CT1151 - Painel de admin com dados reais: listas filtradas por busca/papel/situacao e visao geral com valor movimentado e series por dia")
    void painel() {
        Reserva r = new Reserva();
        r.setImovel(imovel);
        r.setCliente(cliente);
        r.setHospedeNome("Maria Souza");
        r.setHospedeEmail(cliente.getEmail());
        r.setDataCheckin(LocalDate.of(2026, 12, 10));
        r.setDataCheckout(LocalDate.of(2026, 12, 13));
        r.setStatus(StatusReserva.CONFIRMADA);
        r.setNumeroDiarias(3);
        r.setNumeroHospedes(2);
        r.setPrecoDiariaSnapshot(new BigDecimal("300.00"));
        r.setTotalSnapshot(new BigDecimal("900.00"));
        r.setValorTotal(new BigDecimal("900.00"));
        r.setLimiteHospedesSnapshot(4);
        r.setImovelTituloSnapshot("Apto Canasvieiras");
        r.setDataCriacao(LocalDateTime.of(2026, 10, 3, 12, 0));
        em.persist(r);
        em.flush();

        DataDeletionReviewService exclusoes = mock(DataDeletionReviewService.class);
        when(exclusoes.contarEmAndamento()).thenReturn(2L);
        TelemetriaAnaliseService telemetria = mock(TelemetriaAnaliseService.class);
        when(telemetria.sessoesNoDetalhe(30)).thenReturn(3L);
        AdminService admin = new AdminService(usuarios, imoveis, reservas, exclusoes, denuncias, telemetria, relogio);

        assertEquals(1, admin.usuarios("maria", null, 0, 10).total());
        assertEquals(1, admin.usuarios(null, "anfitriao", 0, 10).total());
        assertEquals(0, admin.usuarios("zzz%", null, 0, 10).total(), "% digitado nao vira curinga");
        assertEquals(4, admin.usuarios(null, null, 0, 1000).total() + 1, "tamanho limitado, total intacto: admin, gestor, cliente");
        assertEquals(1, admin.imoveis("canasvieiras", "PUBLICADO", 0, 10).total());
        assertEquals("Centro, Florianopolis", admin.imoveis(null, null, 0, 10).itens().get(0).local());
        assertEquals(0, admin.imoveis(null, "EM_EDICAO", 0, 10).total());
        assertEquals(1, admin.reservas("maria", "CONFIRMADA", 0, 10).total());
        assertEquals(0, admin.reservas(null, "PENDENTE", 0, 10).total());
        assertEquals(1, admin.reservas(null, "STATUS_INVALIDO", 0, 10).total(), "situacao desconhecida nao filtra");

        var v = admin.visaoGeral(30);
        assertEquals(3, v.totalUsuarios());
        assertEquals(new BigDecimal("900.00"), v.valorMovimentado().setScale(2));
        assertEquals(1, v.reservasNoPeriodo());
        assertEquals(2, v.solicitacoesExclusaoAbertas());
        assertEquals(3, v.sessoesNoDetalhe());
        assertEquals(1.0 / 3, v.taxaConversao(), 0.0001);
        assertEquals(1, v.reservasPorDia().size());
        assertFalse(v.cadastrosPorDia().isEmpty());
        assertEquals(3, v.usuariosPorPapel().stream().mapToLong(c -> c.total()).sum());
    }
}
