package br.com.unisenai.smartrent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;

/**
 * Valida o ID token emitido pelo "Entrar com o Google".
 *
 * <p>Quem confere assinatura e validade é o endpoint {@code tokeninfo} do
 * Google; aqui se confere o que é nosso: o token foi emitido para o
 * <em>nosso</em> client ID (sem isso, um token de outro aplicativo serviria) e o
 * e-mail está verificado.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final Set<String> EMISSORES = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient restClient;
    private final String clientId;

    public GoogleTokenVerifier(RestClient.Builder builder,
                               @Value("${smartrent.google.client-id:}") String clientId,
                               @Value("${smartrent.google.tokeninfo-url:https://oauth2.googleapis.com}")
                               String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean configurado() {
        return clientId != null && !clientId.isBlank();
    }

    public IdentidadeGoogle verificar(String idToken) {
        if (!configurado()) {
            throw new LoginGoogleIndisponivelException(
                    "Login com o Google não está configurado (GOOGLE_CLIENT_ID).");
        }

        TokenInfo info;
        try {
            info = restClient.get()
                    .uri(u -> u.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (HttpClientErrorException e) {
            // 400 do Google: token malformado, expirado ou com assinatura inválida.
            throw new TokenGoogleInvalidoException("Credencial do Google inválida ou expirada.");
        } catch (RestClientException e) {
            log.warn("Falha ao consultar o Google: {}", e.getMessage());
            throw new LoginGoogleIndisponivelException(
                    "Não foi possível falar com o Google agora. Tente novamente.");
        }

        if (info == null || !clientId.equals(info.aud()) || !EMISSORES.contains(info.iss())) {
            throw new TokenGoogleInvalidoException("Credencial do Google não pertence a este aplicativo.");
        }
        if (!"true".equals(info.emailVerificado()) || info.email() == null || info.email().isBlank()) {
            throw new TokenGoogleInvalidoException("O e-mail da conta Google não está verificado.");
        }
        return new IdentidadeGoogle(info.email(), info.name());
    }

    public record IdentidadeGoogle(String email, String nome) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInfo(String aud,
                     String iss,
                     String email,
                     @JsonProperty("email_verified") String emailVerificado,
                     String name) {
    }

    public static class TokenGoogleInvalidoException extends RuntimeException {
        public TokenGoogleInvalidoException(String m) {
            super(m);
        }
    }

    public static class LoginGoogleIndisponivelException extends RuntimeException {
        public LoginGoogleIndisponivelException(String m) {
            super(m);
        }
    }
}
