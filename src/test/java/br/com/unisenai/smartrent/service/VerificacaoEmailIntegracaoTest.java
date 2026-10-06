package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.VerificacaoEmail;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.repository.VerificacaoEmailRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Verificacao do e-mail da conta: link de uso unico (so o hash no banco), expiracao, reenvio com limite e auditoria. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:verificacaoemail;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({VerificacaoEmailService.class, AuditoriaContaService.class, LimitadorDeTaxa.class, TextosPoliticas.class,
        VerificacaoEmailIntegracaoTest.Config.class})
class VerificacaoEmailIntegracaoTest {

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 5, 15, 0));
        }

        @Bean
        PerfilProperties perfilProperties() {
            return PerfilProperties.padrao();
        }
    }

    private static final Pattern LINK = Pattern.compile("verificar-email\\.html\\?token=([A-Za-z0-9_\\-]+)");
    private static final String MSG_INVALIDO = "Este link é inválido ou expirou. Peça um novo link de verificação no SmartChat ou no seu perfil.";

    @Autowired private TestEntityManager em;
    @Autowired private VerificacaoEmailService service;
    @Autowired private RelogioFalso relogio;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VerificacaoEmailRepository verificacoes;
    @Autowired private AuditoriaContaRepository auditoria;
    @Autowired private LimitadorDeTaxa limitador;
    @MockBean private EmailSender emailSender;

    private Usuario ana;

    @BeforeEach
    void preparar() {
        Usuario u = new Usuario();
        u.setNome("Ana Tavares");
        u.setEmail("ana" + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(PapelUsuario.CLIENTE);
        u.setAtivo(true);
        em.persist(u);
        em.flush();
        ana = u;
        limitador.limpar("email-verificacao:" + u.getId());
    }

    private void confirmarTransacao() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    /** Envia o link, confirma a transacao (o e-mail sai depois do commit) e devolve o token do link. */
    private String enviarEObterToken() {
        service.enviarLink(ana, "1.1.1.1");
        confirmarTransacao();
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).enviar(eq(ana.getEmail()), anyString(), corpo.capture());
        Matcher m = LINK.matcher(corpo.getValue());
        assertTrue(m.find(), corpo.getValue());
        return m.group(1);
    }

    @Test
    @DisplayName("Cadastro: o link vai ao e-mail da conta so depois do commit; o banco guarda so o hash, com validade")
    void linkEnviadoDepoisDoCommit() {
        service.enviarLink(ana, "1.1.1.1");
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        confirmarTransacao();
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviar(eq(ana.getEmail()), anyString(), corpo.capture());
        Matcher m = LINK.matcher(corpo.getValue());
        assertTrue(m.find());
        String token = m.group(1);

        VerificacaoEmail v = verificacoes.abertas(ana.getId()).get(0);
        assertEquals(TokenSeguro.hash(token), v.getTokenHash());
        assertNotEquals(token, v.getTokenHash());
        assertEquals(Duration.ofHours(24), Duration.between(v.getCriadaEm(), v.getExpiraEm()));
        assertTrue(auditoria.findAll().stream().anyMatch(a -> AuditoriaContaService.EMAIL_VERIFICACAO_ENVIADA.equals(a.getAcao())));
    }

    @Test
    @DisplayName("Confirmar o link verifica o e-mail e audita; a conta ja nasceu sem verificacao")
    void confirmaOLink() {
        assertFalse(ana.isEmailVerificado());
        String token = enviarEObterToken();
        service.confirmar(token, "2.2.2.2");
        em.flush();
        em.clear();
        Usuario relida = usuarios.findById(ana.getId()).orElseThrow();
        assertTrue(relida.isEmailVerificado());
        assertEquals(relogio.instant(), relida.getEmailVerificadoEm());
        assertTrue(auditoria.findAll().stream().anyMatch(a -> AuditoriaContaService.EMAIL_VERIFICADO.equals(a.getAcao())));
    }

    @Test
    @DisplayName("Link reutilizado e recusado (uso unico), com a mesma mensagem de qualquer link invalido")
    void linkReutilizado() {
        String token = enviarEObterToken();
        service.confirmar(token, "2.2.2.2");
        confirmarTransacao();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.confirmar(token, "2.2.2.2"));
        assertEquals(MSG_INVALIDO, e.getMessage());
    }

    @Test
    @DisplayName("Link expirado e recusado e nao verifica a conta")
    void linkExpirado() {
        String token = enviarEObterToken();
        relogio.avancar(Duration.ofHours(24).plusMinutes(1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.confirmar(token, "2.2.2.2"));
        assertEquals(MSG_INVALIDO, e.getMessage());
        assertFalse(usuarios.findById(ana.getId()).orElseThrow().isEmailVerificado());
    }

    @Test
    @DisplayName("Token inexistente, vazio ou enorme recebe a mesma recusa")
    void tokenInvalido() {
        for (String t : new String[]{"nao-existe", "", "   ", null, "x".repeat(500)}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.confirmar(t, "3.3.3.3"));
            assertEquals(MSG_INVALIDO, e.getMessage());
        }
    }

    @Test
    @DisplayName("Reenvio substitui o link anterior (so um pendente) e respeita o limite de taxa")
    void reenvioSubstituiELimita() {
        String primeiro = enviarEObterToken();
        service.reenviar(ana, "1.1.1.1");
        confirmarTransacao();
        assertEquals(1, verificacoes.abertas(ana.getId()).size(), "um unico pendente");
        assertThrows(IllegalArgumentException.class, () -> service.confirmar(primeiro, "1.1.1.1"), "o link antigo foi cancelado");

        int limite = PerfilProperties.padrao().emailPedidosPorHora();
        assertThrows(LimiteExcedidoException.class, () -> {
            for (int i = 0; i <= limite; i++) {
                service.reenviar(ana, "1.1.1.1");
            }
        });
    }

    @Test
    @DisplayName("Conta ja verificada: o reenvio nao envia nada")
    void jaVerificada() {
        ana.setEmailVerificadoEm(relogio.instant());
        em.persist(ana);
        em.flush();
        assertEquals("Seu e-mail já está verificado.", service.reenviar(ana, "1.1.1.1"));
        confirmarTransacao();
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("marcarVerificado (Google ou troca de e-mail) so grava se ainda nao estava verificada")
    void marcarVerificado() {
        service.marcarVerificado(ana);
        assertEquals(relogio.instant(), ana.getEmailVerificadoEm());
        java.time.Instant primeira = ana.getEmailVerificadoEm();
        relogio.avancar(Duration.ofHours(1));
        service.marcarVerificado(ana);
        assertEquals(primeira, ana.getEmailVerificadoEm());
    }
}
