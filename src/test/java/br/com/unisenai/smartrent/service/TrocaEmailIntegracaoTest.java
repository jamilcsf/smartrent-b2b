package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.model.AuditoriaConta;
import br.com.unisenai.smartrent.model.TrocaEmail;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.TrocaEmailRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/** Troca de e-mail: reautenticacao, resposta neutra, token de uso unico (so o hash no banco), expiracao e sessoes. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trocaemail;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({AccountRestrictionService.class, PerfilService.class, NomeExibicaoValidador.class, MessageFilterService.class, AuditoriaContaService.class,
        LimitadorDeTaxa.class, ReautenticacaoService.class, TrocaEmailService.class, TextosPoliticas.class,
        TrocaEmailIntegracaoTest.Config.class})
class TrocaEmailIntegracaoTest {

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0));
        }

        @Bean
        br.com.unisenai.smartrent.config.ExclusaoDadosProperties exclusaoDadosProperties() {
            return br.com.unisenai.smartrent.config.ExclusaoDadosProperties.padrao();
        }

        @Bean
        ChatProperties chatProperties() {
            return ChatProperties.padrao();
        }

        @Bean
        PerfilProperties perfilProperties() {
            return PerfilProperties.padrao();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    private static final String SENHA = "Cavalo-Azul-Mesa-77";
    private static final Pattern LINK = Pattern.compile("confirmar-email\\.html\\?token=([A-Za-z0-9_\\-]+)");

    @Autowired private TestEntityManager em;
    @Autowired private TrocaEmailService service;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RelogioFalso relogio;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrocaEmailRepository trocaRepository;
    @Autowired private AuditoriaContaRepository auditoriaRepository;
    @Autowired private LimitadorDeTaxa limitador;
    @MockBean private EmailSender emailSender;
    @MockBean private GoogleTokenVerifier googleVerifier;

    private Usuario ana;
    private Usuario bia;

    private Usuario conta(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash(encoder.encode(SENHA));
        u.setPapel(PapelUsuario.CLIENTE);
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
        ana = conta("Ana Tavares");
        bia = conta("Bia Tavares");
    }

    /** Pede a troca, confirma a transacao e devolve o token enviado no link ao novo e-mail. */
    private String pedirEObterToken(Usuario u, String novoEmail) {
        Mockito.clearInvocations(emailSender);
        service.solicitar(u, novoEmail, SENHA, null, "10.0.0.1");
        confirmarTransacao();
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSender).enviar(eq(novoEmail), anyString(), corpo.capture());
        Matcher m = LINK.matcher(corpo.getValue());
        assertTrue(m.find(), "o e-mail ao novo endereco traz o link com o token");
        return m.group(1);
    }

    @Test
    @DisplayName("CT471 - Pedido: exige reautenticacao, guarda so o HASH do token, envia link ao novo e aviso ao antigo; o e-mail da conta nao muda ate confirmar")
    void pedido() {
        String antigo = ana.getEmail();
        String token = pedirEObterToken(ana, "novo.ana@exemplo.com");

        Mockito.verify(emailSender).enviar(eq(antigo), anyString(), Mockito.contains("Ana"));
        TrocaEmail t = trocaRepository.abertas(ana.getId()).get(0);
        assertNotEquals(token, t.getTokenHash(), "o token nao e guardado em claro");
        assertEquals(TokenSeguro.hash(token), t.getTokenHash());
        assertEquals(64, t.getTokenHash().length());
        assertEquals(Duration.ofMinutes(60), Duration.between(t.getCriadaEm(), t.getExpiraEm()));
        assertEquals(antigo, usuarioRepository.findById(ana.getId()).orElseThrow().getEmail(), "o login segue com o e-mail antigo");
    }

    @Test
    @DisplayName("CT472 - Senha atual errada, formato invalido e mesmo e-mail sao recusados sem enviar nada")
    void recusas() {
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, "novo@exemplo.com", "errada-demais-1", null, "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, "isto-nao-e-email", SENHA, null, "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.solicitar(ana, ana.getEmail().toUpperCase(), SENHA, null, "ip"));
        Mockito.verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("CT473 - Resposta neutra: e-mail livre e e-mail ja usado recebem exatamente a mesma mensagem; para o ja usado nada e enviado")
    void respostaNeutra() {
        String livre = service.solicitar(ana, "livre@exemplo.com", SENHA, null, "ip");
        String usado = service.solicitar(ana, bia.getEmail(), SENHA, null, "ip");
        assertEquals(livre, usado);
        confirmarTransacao();

        Mockito.verify(emailSender, Mockito.never()).enviar(eq(bia.getEmail()), anyString(), anyString());
        // O pedido para o endereco ja usado cancelou o pendente anterior e nao criou outro: nao ha o que confirmar.
        assertTrue(trocaRepository.abertas(ana.getId()).isEmpty());
    }

    @Test
    @DisplayName("CT474 - Confirmar: troca o e-mail, sobe a versao da sessao (outras sessoes caem), consome o token, avisa o antigo e audita")
    void confirmar() {
        String antigo = ana.getEmail();
        String token = pedirEObterToken(ana, "ana.nova@exemplo.com");
        Mockito.clearInvocations(emailSender);

        service.confirmar(token, "10.0.0.2");
        confirmarTransacao();

        Usuario depois = usuarioRepository.findById(ana.getId()).orElseThrow();
        assertEquals("ana.nova@exemplo.com", depois.getEmail());
        assertEquals(1, depois.getSessaoVersao());
        assertTrue(trocaRepository.abertas(ana.getId()).isEmpty());
        Mockito.verify(emailSender).enviar(eq(antigo), anyString(), Mockito.contains("Ana"));
        List<String> acoes = auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).stream().map(AuditoriaConta::getAcao).toList();
        assertTrue(acoes.contains(AuditoriaContaService.EMAIL_TROCA_CONFIRMADA));
        assertTrue(acoes.contains(AuditoriaContaService.EMAIL_TROCA_SOLICITADA));
        for (AuditoriaConta a : auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId())) {
            assertFalse(String.valueOf(a.getDetalhes()).contains("@"), "a auditoria nao guarda enderecos de e-mail");
        }
    }

    @Test
    @DisplayName("CT475 - O link vale uma vez so: reutilizar, adulterar ou enviar lixo devolve a mesma mensagem de link invalido")
    void tokenDeUsoUnico() {
        String token = pedirEObterToken(ana, "ana.unica@exemplo.com");
        service.confirmar(token, "ip");
        confirmarTransacao();

        IllegalArgumentException reuso = assertThrows(IllegalArgumentException.class, () -> service.confirmar(token, "ip"));
        IllegalArgumentException lixo = assertThrows(IllegalArgumentException.class, () -> service.confirmar("lixo", "ip"));
        IllegalArgumentException vazio = assertThrows(IllegalArgumentException.class, () -> service.confirmar("  ", "ip"));
        assertEquals(reuso.getMessage(), lixo.getMessage());
        assertEquals(reuso.getMessage(), vazio.getMessage());
    }

    @Test
    @DisplayName("CT476 - O link expira (1 hora, relogio injetavel) e nao confirma depois")
    void expira() {
        String token = pedirEObterToken(ana, "ana.expira@exemplo.com");
        relogio.avancar(Duration.ofMinutes(61));
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(token, "ip"));
        assertNotEquals("ana.expira@exemplo.com", usuarioRepository.findById(ana.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("CT477 - Novo pedido substitui o pendente: o link anterior deixa de funcionar")
    void novoPedidoSubstitui() {
        String primeiro = pedirEObterToken(ana, "primeiro@exemplo.com");
        String segundo = pedirEObterToken(ana, "segundo@exemplo.com");

        assertEquals(1, trocaRepository.abertas(ana.getId()).size());
        service.confirmar(segundo, "ip");
        confirmarTransacao(); // a recusa abaixo marca a transacao como rollback-only: vem depois do commit
        assertEquals("segundo@exemplo.com", usuarioRepository.findById(ana.getId()).orElseThrow().getEmail());
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(primeiro, "ip"));
    }

    @Test
    @DisplayName("CT478 - Se outra conta tomou o endereco depois do pedido, a confirmacao e recusada e a conta fica como estava")
    void enderecoTomadoDepois() {
        String antigo = ana.getEmail();
        String token = pedirEObterToken(ana, "disputado@exemplo.com");
        Usuario outra = new Usuario();
        outra.setNome("Outra");
        outra.setEmail("disputado@exemplo.com");
        outra.setSenhaHash(encoder.encode(SENHA));
        outra.setPapel(PapelUsuario.CLIENTE);
        outra.setAtivo(true);
        em.persist(outra);
        em.flush();

        assertThrows(IllegalArgumentException.class, () -> service.confirmar(token, "ip"));
        assertEquals(antigo, usuarioRepository.findById(ana.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("CT479 - Limite de pedidos por hora (3) e de tentativas de confirmacao por IP (20)")
    void rateLimit() {
        for (int i = 0; i < 3; i++) {
            service.solicitar(bia, "pedido" + i + "@exemplo.com", SENHA, null, "ip");
        }
        assertThrows(LimiteExcedidoException.class, () -> service.solicitar(bia, "pedido9@exemplo.com", SENHA, null, "ip"));

        for (int i = 0; i < 20; i++) {
            assertThrows(IllegalArgumentException.class, () -> service.confirmar("token-invalido-" + System.nanoTime(), "9.9.9.9"));
        }
        assertThrows(LimiteExcedidoException.class, () -> service.confirmar("outro-token", "9.9.9.9"));
    }
}
