package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Padrao enquanto nao ha SMTP: registra o e-mail no log. O CORPO (que pode conter o link de
 * confirmacao com token) so e registrado quando {@code smartrent.email.log-corpo=true}, o
 * padrao de desenvolvimento local; em producao, defina EMAIL_LOG_CORPO=false para que links
 * e tokens nunca fiquem em log.
 */
@Component
public class EmailSenderLog implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderLog.class);

    private final boolean logarCorpo;

    public EmailSenderLog(@Value("${smartrent.email.log-corpo:true}") boolean logarCorpo) {
        this.logarCorpo = logarCorpo;
    }

    @Override
    public String canal() {
        return "EMAIL_LOG";
    }

    @Override
    public void enviar(String para, String assunto, String corpo) {
        if (logarCorpo) {
            log.info("[e-mail simulado] para={} assunto=\"{}\" corpo=\"{}\"", para, assunto, corpo);
        } else {
            log.info("[e-mail simulado] para={} assunto=\"{}\" (corpo omitido)", para, assunto);
        }
    }
}
