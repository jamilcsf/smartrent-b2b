package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleTokenVerifierTest {

    private static final String BASE = "https://google.teste";
    private static final String CLIENT_ID = "meu-client-id.apps.googleusercontent.com";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;
    private GoogleTokenVerifier verificador;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        verificador = new GoogleTokenVerifier(builder, CLIENT_ID, BASE);
    }

    private static String info(String aud, String iss, String verificado) {
        return "{\"aud\":\"" + aud + "\",\"iss\":\"" + iss + "\",\"email\":\"ana@gmail.com\","
                + "\"email_verified\":\"" + verificado + "\",\"name\":\"Ana\",\"exp\":\"9999999999\"}";
    }

    @Test
    @DisplayName("CT43 - Google: token válido do nosso aplicativo devolve e-mail e nome")
    void deveAceitarTokenValido() {
        servidor.expect(requestTo(BASE + "/tokeninfo?id_token=abc"))
                .andRespond(withSuccess(info(CLIENT_ID, "https://accounts.google.com", "true"),
                        MediaType.APPLICATION_JSON));

        var identidade = verificador.verificar("abc");

        assertEquals("ana@gmail.com", identidade.email());
        assertEquals("Ana", identidade.nome());
    }

    @Test
    @DisplayName("CT44 - Google: token emitido para outro aplicativo deve ser recusado")
    void deveRecusarAudienciaDiferente() {
        servidor.expect(requestTo(BASE + "/tokeninfo?id_token=abc"))
                .andRespond(withSuccess(info("outro-app", "accounts.google.com", "true"),
                        MediaType.APPLICATION_JSON));

        assertThrows(GoogleTokenVerifier.TokenGoogleInvalidoException.class, () -> verificador.verificar("abc"));
    }

    @Test
    @DisplayName("CT45 - Google: e-mail não verificado deve ser recusado")
    void deveRecusarEmailNaoVerificado() {
        servidor.expect(requestTo(BASE + "/tokeninfo?id_token=abc"))
                .andRespond(withSuccess(info(CLIENT_ID, "accounts.google.com", "false"),
                        MediaType.APPLICATION_JSON));

        assertThrows(GoogleTokenVerifier.TokenGoogleInvalidoException.class, () -> verificador.verificar("abc"));
    }

    @Test
    @DisplayName("CT46 - Google: token rejeitado (400) pelo Google deve ser recusado")
    void deveRecusarTokenRejeitadoPeloGoogle() {
        servidor.expect(requestTo(BASE + "/tokeninfo?id_token=lixo"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(GoogleTokenVerifier.TokenGoogleInvalidoException.class, () -> verificador.verificar("lixo"));
    }

    @Test
    @DisplayName("CT47 - Google: sem client ID configurado o login fica indisponível")
    void deveFicarIndisponivelSemClientId() {
        var semConfig = new GoogleTokenVerifier(builder, "", BASE);

        assertThrows(GoogleTokenVerifier.LoginGoogleIndisponivelException.class, () -> semConfig.verificar("abc"));
    }
}
