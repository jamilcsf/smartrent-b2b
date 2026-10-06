package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Envio pela API da Brevo, sem rede: o servidor HTTP e simulado. */
class EmailSenderBrevoTest {

    private MockRestServiceServer servidor;
    private EmailSenderBrevo sender;

    private void montar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
        servidor = MockRestServiceServer.bindTo(builder).build();
        sender = new EmailSenderBrevo(builder.build(), "chave-secreta-de-teste", "no-reply@smartrent.dev", "SmartRent");
    }

    @Test
    @DisplayName("Envia POST /v3/smtp/email com a chave no cabecalho api-key e remetente, destinatario, assunto e corpo no JSON")
    void enviaPeloEndpointCerto() {
        montar();
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "chave-secreta-de-teste"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("no-reply@smartrent.dev"))
                .andExpect(jsonPath("$.sender.name").value("SmartRent"))
                .andExpect(jsonPath("$.to[0].email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.subject").value("Confirme o seu e-mail"))
                .andExpect(jsonPath("$.textContent").value("Abra o link: https://x/verificar-email.html?token=abc"))
                .andRespond(withSuccess("{\"messageId\":\"<1@brevo>\"}", MediaType.APPLICATION_JSON));
        sender.enviar("ana@exemplo.com", "Confirme o seu e-mail", "Abra o link: https://x/verificar-email.html?token=abc");
        servidor.verify();
        assertEquals("EMAIL_BREVO", sender.canal());
    }

    @Test
    @DisplayName("Recusa da Brevo (401, 400) ou falha de rede nao lancam excecao: o envio nunca desfaz a acao de origem")
    void falhaNaoPropaga() {
        montar();
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Key not found\"}").contentType(MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> sender.enviar("ana@exemplo.com", "a", "b"));
        servidor.verify();

        montar();
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertDoesNotThrow(() -> sender.enviar("ana@exemplo.com", "a", "b"));

        montar();
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(request -> { throw new java.io.IOException("rede fora do ar"); });
        assertDoesNotThrow(() -> sender.enviar("ana@exemplo.com", "a", "b"));
    }
}
