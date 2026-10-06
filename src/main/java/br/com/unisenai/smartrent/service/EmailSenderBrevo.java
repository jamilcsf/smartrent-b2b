package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio real de e-mail pela API HTTPS da Brevo (antiga Sendinblue), com o {@link RestClient} sincrono do Spring.
 *
 * <p>Por que API e nao SMTP: o plano gratuito do Render bloqueia as portas SMTP de saida (25, 465 e 587).
 * A Brevo aceita um unico remetente verificado (nao exige dominio proprio) e o plano gratuito atende o prototipo.
 *
 * <p>So entra em uso quando {@code BREVO_API_KEY} esta definida; sem ela vale o {@link EmailSenderLog}. A chave e o
 * remetente vem do ambiente (RNF03). O corpo (que pode ter link com token) e os enderecos NUNCA vao para o log: em caso
 * de falha registra-se so o status HTTP. Falha de envio nao desfaz a acao que a originou (ver {@link AposCommit}).
 */
@Component
@Primary
@ConditionalOnExpression("!'${smartrent.email.brevo-api-key:}'.isEmpty()")
public class EmailSenderBrevo implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderBrevo.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String remetenteEmail;
    private final String remetenteNome;

    @Autowired
    public EmailSenderBrevo(RestClient.Builder builder,
                            @Value("${smartrent.email.brevo-api-key}") String apiKey,
                            @Value("${smartrent.email.remetente:}") String remetenteEmail,
                            @Value("${smartrent.email.remetente-nome:SmartRent}") String remetenteNome,
                            @Value("${smartrent.email.timeout-ms:10000}") long timeoutMs) {
        this(comTimeout(builder, timeoutMs), apiKey, remetenteEmail, remetenteNome);
        if (remetenteEmail == null || remetenteEmail.isBlank()) {
            log.warn("BREVO_API_KEY definida, mas EMAIL_REMETENTE esta vazio: a Brevo recusara os envios "
                    + "(o remetente precisa ser um endereco verificado na conta).");
        }
    }

    /** Construtor para testes: recebe o cliente HTTP ja montado. */
    EmailSenderBrevo(RestClient restClient, String apiKey, String remetenteEmail, String remetenteNome) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.remetenteEmail = remetenteEmail;
        this.remetenteNome = remetenteNome;
    }

    private static RestClient comTimeout(RestClient.Builder builder, long timeoutMs) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        return builder.baseUrl("https://api.brevo.com").requestFactory(fabrica).build();
    }

    @Override
    public String canal() {
        return "EMAIL_BREVO";
    }

    @Override
    public void enviar(String para, String assunto, String corpo) {
        Map<String, Object> pedido = Map.of(
                "sender", Map.of("name", remetenteNome, "email", remetenteEmail),
                "to", List.of(Map.of("email", para)),
                "subject", assunto,
                "textContent", corpo);
        try {
            restClient.post().uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Sem o corpo da resposta nem os enderecos: so o status ajuda a diagnosticar (401 = chave, 400 = remetente).
            log.warn("Brevo recusou o envio de e-mail (HTTP {}).", e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.warn("Falha ao chamar a Brevo ({}).", e.getClass().getSimpleName());
        }
    }
}
