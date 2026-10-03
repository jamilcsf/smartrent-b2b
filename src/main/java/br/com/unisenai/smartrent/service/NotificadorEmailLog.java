package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Padrao enquanto nao ha SMTP: apenas registra o e-mail que seria enviado. O
 * canal se chama EMAIL_LOG de proposito, para a auditoria nao fingir que um
 * e-mail saiu. Para enviar de verdade, registre outro bean {@link NotificadorEmail}
 * marcado como {@code @Primary}.
 */
@Component
public class NotificadorEmailLog implements NotificadorEmail {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEmailLog.class);

    @Override
    public String canal() {
        return "EMAIL_LOG";
    }

    @Override
    public void enviar(Usuario destino, String assunto, String corpo) {
        log.info("[e-mail simulado] para={} assunto=\"{}\" corpo=\"{}\"", destino.getEmail(), assunto, corpo);
    }
}
