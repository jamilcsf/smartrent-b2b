package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CaptchaServiceTest {

    private static final String URL = "https://captcha.teste/siteverify";

    private MockRestServiceServer servidor;
    private CaptchaService captcha;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        captcha = new CaptchaService(builder, "site", "segredo", URL);
    }

    @Test
    @DisplayName("CT39 - Captcha: token aprovado pelo Google deve passar, enviando o segredo")
    void deveAceitarTokenAprovado() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=segredo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=tok")))
                .andRespond(withSuccess("{\"success\":true,\"challenge_ts\":\"x\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> captcha.verificar("tok"));
        servidor.verify();
    }

    @Test
    @DisplayName("CT40 - Captcha: token reprovado pelo Google deve ser recusado")
    void deveRecusarTokenReprovado() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(CaptchaService.CaptchaInvalidoException.class, () -> captcha.verificar("tok"));
    }

    @Test
    @DisplayName("CT41 - Captcha: token ausente é recusado sem sequer consultar o Google")
    void deveRecusarTokenAusente() {
        assertThrows(CaptchaService.CaptchaInvalidoException.class, () -> captcha.verificar(null));
        assertThrows(CaptchaService.CaptchaInvalidoException.class, () -> captcha.verificar("  "));
        servidor.verify(); // nenhuma requisição esperada nem feita
    }

    @Test
    @DisplayName("CT42 - Captcha: se o Google estiver fora do ar, o login é recusado (falha fechada)")
    void deveFalharFechadoSeGoogleCair() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(CaptchaService.CaptchaInvalidoException.class, () -> captcha.verificar("tok"));
    }
}
