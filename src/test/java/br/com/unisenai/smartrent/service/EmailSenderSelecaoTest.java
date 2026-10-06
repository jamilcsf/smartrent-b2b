package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/** Qual {@link EmailSender} vale: so log sem BREVO_API_KEY; a Brevo (primaria) quando a chave existe. */
class EmailSenderSelecaoTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(EmailSenderLog.class, EmailSenderBrevo.class);

    @Test
    @DisplayName("Sem BREVO_API_KEY (vazia, como no application.properties) so o EmailSenderLog existe")
    void semChaveSoLog() {
        runner.withPropertyValues("smartrent.email.brevo-api-key=").run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(EmailSender.class).size());
            assertEquals("EMAIL_LOG", ctx.getBean(EmailSender.class).canal());
        });
        runner.run(ctx -> assertEquals("EMAIL_LOG", ctx.getBean(EmailSender.class).canal()));
    }

    @Test
    @DisplayName("Com BREVO_API_KEY a Brevo e a primaria e quem recebe os envios")
    void comChaveBrevoVence() {
        runner.withPropertyValues("smartrent.email.brevo-api-key=abc", "smartrent.email.remetente=no-reply@smartrent.dev")
                .run(ctx -> {
                    assertEquals(2, ctx.getBeansOfType(EmailSender.class).size());
                    assertEquals("EMAIL_BREVO", ctx.getBean(EmailSender.class).canal());
                });
    }
}
