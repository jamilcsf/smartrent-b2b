package br.com.unisenai.smartrent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Valida o token do reCAPTCHA v2 junto ao Google.
 *
 * <p>A verificação é sempre feita no servidor: um captcha conferido só no
 * navegador não impede nada, pois quem chama a API direto o ignora. Se o Google
 * não puder ser consultado, o login é recusado (falha fechada).
 */
@Service
public class CaptchaService implements EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** Chave secreta de teste publicada pelo Google: aprova qualquer token. */
    static final String SECRET_DE_TESTE = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe";

    private final RestClient restClient;
    private final String siteKey;
    private final String secret;
    private boolean perfilDevOuTeste = true; // sem Environment (testes unitarios) nao ha o que validar

    public CaptchaService(RestClient.Builder builder,
                          @Value("${smartrent.captcha.site-key:}") String siteKey,
                          @Value("${smartrent.captcha.secret:}") String secret,
                          @Value("${smartrent.captcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
                          String verifyUrl) {
        this.restClient = builder.baseUrl(verifyUrl).build();
        this.siteKey = siteKey;
        this.secret = secret;
    }

    @Override
    public void setEnvironment(Environment ambiente) {
        this.perfilDevOuTeste = ambiente.acceptsProfiles(Profiles.of("dev", "test"));
    }

    /**
     * A chave de teste do Google aprova qualquer token: fora de dev/test a aplicacao nao sobe com ela, senao o
     * captcha de login e cadastro viraria enfeite.
     */
    @PostConstruct
    void validarChave() {
        if (!SECRET_DE_TESTE.equals(secret)) {
            return;
        }
        if (!perfilDevOuTeste) {
            throw new IllegalStateException("RECAPTCHA_SECRET e a chave de TESTE do Google (aprova qualquer token)."
                    + " Defina RECAPTCHA_SITE_KEY e RECAPTCHA_SECRET reais (RNF03)."
                    + " Para desenvolvimento local, ative o perfil 'dev' (SPRING_PROFILES_ACTIVE=dev).");
        }
        log.warn("reCAPTCHA usando a chave de TESTE do Google: qualquer token é aprovado (perfil dev/test).");
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void verificar(String token) {
        if (token == null || token.isBlank()) {
            throw new CaptchaInvalidoException("Confirme que você não é um robô.");
        }
        if (secret == null || secret.isBlank()) {
            throw new CaptchaInvalidoException("Verificação indisponível: captcha não configurado.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);

        RespostaGoogle resposta;
        try {
            resposta = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RespostaGoogle.class);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar o reCAPTCHA: {}", e.getMessage());
            throw new CaptchaInvalidoException("Não foi possível validar o captcha agora. Tente novamente.");
        }

        if (resposta == null || !resposta.success()) {
            throw new CaptchaInvalidoException("Verificação do captcha falhou. Tente novamente.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaGoogle(boolean success) {
    }

    public static class CaptchaInvalidoException extends RuntimeException {
        public CaptchaInvalidoException(String m) {
            super(m);
        }
    }
}
