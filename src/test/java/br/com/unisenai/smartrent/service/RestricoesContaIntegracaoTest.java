package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.dto.SenhaRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.TrocaEmailRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
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

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Restricoes de conta durante o pedido de exclusao de dados, nos fluxos do PERFIL: o e-mail nao muda
 * (nem por link pedido antes); a senha continua podendo ser trocada; cancelar libera tudo.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:restricoesconta;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({PerfilService.class, NomeExibicaoValidador.class, MessageFilterService.class, AuditoriaContaService.class,
        LimitadorDeTaxa.class, ReautenticacaoService.class, ExclusaoDadosService.class, AccountRestrictionService.class,
        SinaisDeRisco.class, TextosPoliticas.class, SenhaService.class, PoliticaDeSenha.class, TrocaEmailService.class,
        RestricoesContaIntegracaoTest.Config.class})
class RestricoesContaIntegracaoTest {

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
        PerfilProperties perfilProperties() {
            return PerfilProperties.padrao();
        }

        @Bean
        ExclusaoDadosProperties exclusaoProps() {
            return ExclusaoDadosProperties.padrao();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-bytes-para-hmac-sha-256-ok", 3600);
        }
    }

    private static final String SENHA = "Cavalo-Azul-Mesa-77";
    private static final String NOVA = "Girafa-Verde-Pista-91";
    private static final Pattern LINK = Pattern.compile("confirmar-email\\.html\\?token=([A-Za-z0-9_\\-]+)");

    @Autowired private TestEntityManager em;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ExclusaoDadosService exclusao;
    @Autowired private SenhaService senhaService;
    @Autowired private TrocaEmailService trocaEmail;
    @Autowired private AccountRestrictionService restricoes;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrocaEmailRepository trocaRepository;
    @MockBean private EmailSender emailSender;
    @MockBean private GoogleTokenVerifier googleVerifier;

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

    private void descartarTransacao() {
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
    }

    @BeforeEach
    void preparar() {
        ana = conta("Ana Pires", PapelUsuario.CLIENTE);
        gestor = conta("Gil Pires", PapelUsuario.ANFITRIAO);
    }

    @Test
    @DisplayName("CT496 - Durante o pedido: a senha continua podendo ser trocada, mas o e-mail nao pode ser alterado (pedido recusado)")
    void senhaLivreEmailRestrito() {
        exclusao.solicitar(ana, null, SENHA, null, "ip");

        assertDoesNotThrow(() -> senhaService.alterar(ana, new SenhaRequest(SENHA, null, NOVA, NOVA), "ip"),
                "trocar a senha ajuda o dono legitimo a retomar a conta");
        AcessoNegadoException e = assertThrows(AcessoNegadoException.class,
                () -> trocaEmail.solicitar(ana, "novo@exemplo.com", NOVA, null, "ip"));
        assertTrue(e.getMessage().contains("e-mail"), e.getMessage());
        assertTrue(trocaRepository.abertas(ana.getId()).isEmpty());
        assertEquals(java.util.List.of("NOVA_RESERVA", "ALTERAR_EMAIL"), restricoes.restricoesAtivas(ana));
        assertEquals(java.util.List.of("ANUNCIOS_SEM_NOVAS_RESERVAS", "PUBLICAR_ANUNCIO", "CONFIRMAR_EDICAO", "ALTERAR_EMAIL"),
                java.util.stream.Stream.of("x").flatMap(x -> {
                    exclusao.solicitar(gestor, null, SENHA, null, "ip");
                    return restricoes.restricoesAtivas(gestor).stream();
                }).toList());
    }

    @Test
    @DisplayName("CT497 - Link de troca de e-mail pedido ANTES do pedido de exclusao tambem e recusado; o token segue valido e confirma depois que o pedido acaba")
    void linkAnteriorRecusadoAteOPedidoAcabar() {
        Mockito.clearInvocations(emailSender);
        trocaEmail.solicitar(ana, "ana.nova@exemplo.com", SENHA, null, "ip");
        confirmarTransacao();
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailSender).enviar(eq("ana.nova@exemplo.com"), anyString(), corpo.capture());
        Matcher m = LINK.matcher(corpo.getValue());
        assertTrue(m.find());
        String token = m.group(1);
        String antigo = usuarioRepository.findById(ana.getId()).orElseThrow().getEmail();

        exclusao.solicitar(ana, null, SENHA, null, "ip");
        confirmarTransacao();
        assertThrows(AcessoNegadoException.class, () -> trocaEmail.confirmar(token, "9.9.9.9"));
        descartarTransacao(); // a recusa deixa a transacao como rollback-only: desfaz tudo (nada foi consumido)
        assertEquals(antigo, usuarioRepository.findById(ana.getId()).orElseThrow().getEmail(), "o e-mail nao mudou");
        assertEquals(1, trocaRepository.abertas(ana.getId()).size(), "o token nao foi consumido");

        exclusao.cancelar(ana, "ip");
        confirmarTransacao();
        trocaEmail.confirmar(token, "9.9.9.9");
        confirmarTransacao();
        assertEquals("ana.nova@exemplo.com", usuarioRepository.findById(ana.getId()).orElseThrow().getEmail());
    }
}
