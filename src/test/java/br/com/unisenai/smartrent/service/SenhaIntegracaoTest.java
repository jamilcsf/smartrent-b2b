package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.SenhaRequest;
import br.com.unisenai.smartrent.model.AuditoriaConta;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Troca de senha com banco de verdade (H2): reautenticacao, politica, sessoes, auditoria, aviso por e-mail e logs. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:senha;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({AccountRestrictionService.class, PerfilService.class, NomeExibicaoValidador.class, MessageFilterService.class, AuditoriaContaService.class,
        LimitadorDeTaxa.class, SenhaService.class, ReautenticacaoService.class, PoliticaDeSenha.class,
        TextosPoliticas.class, SenhaIntegracaoTest.Config.class})
class SenhaIntegracaoTest {

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
            return new BCryptPasswordEncoder(4); // custo baixo: so para teste
        }

        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-bytes-para-hmac-sha-256-ok", 3600);
        }
    }

    private static final String ATUAL = "Cavalo-Azul-Mesa-77";
    private static final String NOVA = "Girafa-Verde-Pista-91";

    @Autowired private TestEntityManager em;
    @Autowired private SenhaService senhaService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaContaRepository auditoriaRepository;
    @MockBean private EmailSender emailSender;
    @MockBean private GoogleTokenVerifier googleVerifier;

    private Usuario contaComSenha(String nome, String senha) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash(encoder.encode(senha));
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

    @Test
    @DisplayName("CT466 - Trocar a senha: exige a atual, grava BCrypt, sobe a versao da sessao, devolve token novo, audita sem a senha e avisa por e-mail")
    void trocarSenha() {
        Usuario u = contaComSenha("Dora Alves", ATUAL);
        String tokenAntigo = jwtService.gerarToken(u);
        assertEquals(0, jwtService.versaoDaSessao(tokenAntigo));

        AuthResponse r = senhaService.alterar(u, new SenhaRequest(ATUAL, null, NOVA, NOVA), "10.0.0.9");
        confirmarTransacao();

        Usuario depois = usuarioRepository.findById(u.getId()).orElseThrow();
        assertTrue(encoder.matches(NOVA, depois.getSenhaHash()));
        assertFalse(encoder.matches(ATUAL, depois.getSenhaHash()));
        assertEquals(1, depois.getSessaoVersao());
        assertNotEquals(depois.getSessaoVersao(), jwtService.versaoDaSessao(tokenAntigo), "token antigo deixa de valer");
        assertEquals(depois.getSessaoVersao(), jwtService.versaoDaSessao(r.token()), "a sessao atual segue com token novo");

        Mockito.verify(emailSender).enviar(Mockito.eq(depois.getEmail()), Mockito.contains("senha"), Mockito.contains("Dora"));
        List<AuditoriaConta> trilha = auditoriaRepository.findByUsuarioIdOrderByIdDesc(u.getId());
        assertEquals(AuditoriaContaService.SENHA_ALTERADA, trilha.get(0).getAcao());
        for (AuditoriaConta a : trilha) {
            String detalhes = String.valueOf(a.getDetalhes());
            assertFalse(detalhes.contains(NOVA) || detalhes.contains(ATUAL));
        }
    }

    @Test
    @DisplayName("CT467 - Senha atual errada ou ausente, nova igual a atual, confirmacao diferente, fraca ou comum: nada muda e nada e enviado")
    void trocarSenhaRecusas() {
        Usuario u = contaComSenha("Eva Lima", ATUAL);
        String hash = u.getSenhaHash();

        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest("errada-demais-1", null, NOVA, NOVA), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest("", null, NOVA, NOVA), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(null, null, NOVA, NOVA), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, ATUAL, ATUAL), "ip"),
                "igual a atual");
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, NOVA, NOVA + "x"), "ip"),
                "confirmacao diferente");
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, "curta1!", "curta1!"), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, "Password123", "Password123"), "ip"));

        Usuario depois = usuarioRepository.findById(u.getId()).orElseThrow();
        assertEquals(hash, depois.getSenhaHash());
        assertEquals(0, depois.getSessaoVersao());
        Mockito.verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("CT468 - Tentativas erradas da senha atual: 5 por janela; depois nem a senha certa e aceita (429) e as recusas ficam auditadas")
    void trocarSenhaRateLimit() {
        Usuario u = contaComSenha("Fabio Reis", ATUAL);
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> senhaService.alterar(u, new SenhaRequest("palpite-errado-1", null, NOVA, NOVA), "ip"));
        }
        assertThrows(LimiteExcedidoException.class,
                () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, NOVA, NOVA), "ip"));

        long recusas = auditoriaRepository.findByUsuarioIdOrderByIdDesc(u.getId()).stream()
                .filter(a -> AuditoriaContaService.SENHA_TENTATIVA_RECUSADA.equals(a.getAcao())).count();
        assertEquals(5, recusas, "as recusas sao auditadas em transacao propria, sobrevivendo ao rollback");
    }

    @Test
    @DisplayName("CT469 - Conta criada pelo Google: reautentica com a credencial do Google (so se o e-mail for o da conta) e define a primeira senha")
    void contaSoGoogle() {
        Usuario u = contaComSenha("Gabi Nunes", ATUAL);
        u.setSenhaDefinida(false);
        em.flush();
        Mockito.when(googleVerifier.verificar("cred-boa")).thenReturn(new GoogleTokenVerifier.IdentidadeGoogle(u.getEmail(), "Gabi"));
        Mockito.when(googleVerifier.verificar("cred-de-outro")).thenReturn(new GoogleTokenVerifier.IdentidadeGoogle("outro@x.com", "Outro"));
        Mockito.when(googleVerifier.verificar("cred-invalida")).thenThrow(new GoogleTokenVerifier.TokenGoogleInvalidoException("x"));

        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(ATUAL, null, NOVA, NOVA), "ip"),
                "senha nao vale para conta sem senha definida");
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(null, "cred-de-outro", NOVA, NOVA), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(null, "cred-invalida", NOVA, NOVA), "ip"));
        assertThrows(IllegalArgumentException.class, () -> senhaService.alterar(u, new SenhaRequest(null, null, NOVA, NOVA), "ip"));

        senhaService.alterar(u, new SenhaRequest(null, "cred-boa", NOVA, NOVA), "ip");
        Usuario depois = usuarioRepository.findById(u.getId()).orElseThrow();
        assertTrue(depois.isSenhaDefinida());
        assertTrue(encoder.matches(NOVA, depois.getSenhaHash()));
    }

    @Test
    @DisplayName("CT470 - A senha (atual, nova, hash) nunca aparece em log; o toString do pedido e fixo")
    void senhaNuncaEmLog() {
        var raiz = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var captura = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        captura.start();
        raiz.addAppender(captura);
        try {
            Usuario u = contaComSenha("Hugo Dias", ATUAL);
            senhaService.alterar(u, new SenhaRequest(ATUAL, null, NOVA, NOVA), "ip");
            confirmarTransacao(); // a recusa abaixo marca a transacao como rollback-only: vem depois do commit
            assertThrows(IllegalArgumentException.class,
                    () -> senhaService.alterar(u, new SenhaRequest("errada-demais-1", null, NOVA + "!", NOVA + "!"), "ip"));

            String hash = usuarioRepository.findById(u.getId()).orElseThrow().getSenhaHash();
            for (var evento : captura.list) {
                String texto = evento.getFormattedMessage() + " " + evento.getThrowableProxy();
                assertFalse(texto.contains(ATUAL) || texto.contains(NOVA) || texto.contains("errada-demais-1") || texto.contains(hash),
                        "log vazou senha/hash: " + texto);
            }
        } finally {
            raiz.detachAppender(captura);
        }
        assertEquals("SenhaRequest[***]", new SenhaRequest(ATUAL, "g", NOVA, NOVA).toString());
    }
}
