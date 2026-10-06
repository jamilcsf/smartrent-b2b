package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Forca bruta e contas em massa: limites por IP e por e-mail, e a chave de teste do captcha fora de dev. */
class LimitesDeAutenticacaoTest {

    private RelogioFalso relogio;
    private LimitadorDeTaxa limitador;
    private LimitesDeAutenticacao limites;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso(LocalDateTime.of(2026, 10, 5, 12, 0));
        limitador = new LimitadorDeTaxa(relogio);
        limites = new LimitesDeAutenticacao(limitador);
    }

    @Test
    @DisplayName("CT1010 - Cinco senhas erradas para o mesmo e-mail bloqueiam a sexta tentativa, mesmo de outro IP")
    void bloqueiaPorEmail() {
        for (int i = 0; i < LimitesDeAutenticacao.FALHAS_POR_EMAIL; i++) {
            limites.exigirLogin("10.0.0." + i, "Ana@SmartRent.dev");
            limites.registrarFalhaDeLogin("10.0.0." + i, "Ana@SmartRent.dev");
        }
        assertThrows(LimiteExcedidoException.class, () -> limites.exigirLogin("10.9.9.9", " ana@smartrent.dev "));
    }

    @Test
    @DisplayName("CT1011 - O bloqueio expira depois da janela e um login valido zera o contador do e-mail")
    void expiraELimpa() {
        for (int i = 0; i < LimitesDeAutenticacao.FALHAS_POR_EMAIL; i++) {
            limites.registrarFalhaDeLogin("10.0.0.1", "ana@smartrent.dev");
        }
        limites.registrarLoginValido("ana@smartrent.dev");
        assertDoesNotThrow(() -> limites.exigirLogin("10.0.0.2", "ana@smartrent.dev"));

        for (int i = 0; i < LimitesDeAutenticacao.FALHAS_POR_EMAIL; i++) {
            limites.registrarFalhaDeLogin("10.0.0.3", "bia@smartrent.dev");
        }
        assertThrows(LimiteExcedidoException.class, () -> limites.exigirLogin("10.0.0.4", "bia@smartrent.dev"));
        relogio.avancar(LimitesDeAutenticacao.JANELA_LOGIN.plusSeconds(1));
        assertDoesNotThrow(() -> limites.exigirLogin("10.0.0.4", "bia@smartrent.dev"));
    }

    @Test
    @DisplayName("CT1012 - Muitas falhas do mesmo IP (e-mails diferentes) bloqueiam o IP")
    void bloqueiaPorIp() {
        for (int i = 0; i < LimitesDeAutenticacao.FALHAS_POR_IP; i++) {
            limites.registrarFalhaDeLogin("203.0.113.7", "alvo" + i + "@smartrent.dev");
        }
        assertThrows(LimiteExcedidoException.class, () -> limites.exigirLogin("203.0.113.7", "outro@smartrent.dev"));
        assertDoesNotThrow(() -> limites.exigirLogin("203.0.113.8", "outro@smartrent.dev"));
    }

    @Test
    @DisplayName("CT1013 - Cadastro: dez tentativas por hora por IP; a seguinte e recusada")
    void limitaCadastro() {
        for (int i = 0; i < LimitesDeAutenticacao.CADASTROS_POR_IP; i++) {
            limites.exigirCadastro("198.51.100.1");
        }
        assertThrows(LimiteExcedidoException.class, () -> limites.exigirCadastro("198.51.100.1"));
        assertDoesNotThrow(() -> limites.exigirCadastro("198.51.100.2"));
    }

    @Test
    @DisplayName("CT1014 - O limitador descarta chaves ociosas quando o mapa cresce demais")
    void varreChavesOciosas() {
        for (int i = 0; i <= LimitadorDeTaxa.LIMITE_PARA_VARRER; i++) {
            limitador.registrar("k" + i);
        }
        relogio.avancar(LimitadorDeTaxa.RETENCAO.plusMinutes(1));
        limitador.registrar("nova");
        assertTrue(limitador.tamanho() < 10, "chaves ociosas deveriam ter sido varridas: " + limitador.tamanho());
    }

    @Test
    @DisplayName("CT1015 - Chave de teste do reCAPTCHA: recusada fora de dev/test, aceita em dev e test")
    void captchaComChaveDeTeste() {
        CaptchaService producao = new CaptchaService(RestClient.builder(), "site", CaptchaService.SECRET_DE_TESTE, "http://x");
        producao.setEnvironment(new MockEnvironment());
        assertThrows(IllegalStateException.class, producao::validarChave);

        for (String perfil : new String[]{"dev", "test"}) {
            CaptchaService local = new CaptchaService(RestClient.builder(), "site", CaptchaService.SECRET_DE_TESTE, "http://x");
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(perfil);
            local.setEnvironment(env);
            assertDoesNotThrow(local::validarChave);
        }

        CaptchaService real = new CaptchaService(RestClient.builder(), "site", "segredo-real", "http://x");
        real.setEnvironment(new MockEnvironment());
        assertDoesNotThrow(real::validarChave);
        assertEquals(Duration.ofHours(24), LimitadorDeTaxa.RETENCAO);
    }
}
