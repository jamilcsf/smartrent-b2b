package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Jobs de reserva: expira pendentes nao pagas (libera as datas) e retenta
 * reembolsos que falharam. Ambos sao idempotentes e um erro nao derruba o proximo ciclo.
 */
@Component
public class ReservaJobs {

    private static final Logger log = LoggerFactory.getLogger(ReservaJobs.class);

    private final CancelamentoService cancelamento;
    private final ReembolsoService reembolsos;

    public ReservaJobs(CancelamentoService cancelamento, ReembolsoService reembolsos) {
        this.cancelamento = cancelamento;
        this.reembolsos = reembolsos;
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.reserva-pendente-intervalo-ms:60000}", initialDelay = 45_000)
    public void expirarPendentes() {
        try {
            int n = cancelamento.expirarPendentes();
            if (n > 0) {
                log.info("{} reserva(s) pendente(s) expirada(s) sem cobranca.", n);
            }
        } catch (RuntimeException e) {
            log.error("Expiracao de reservas pendentes falhou; tentara de novo no proximo ciclo.", e);
        }
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.reembolso-intervalo-ms:300000}", initialDelay = 60_000)
    public void reprocessarReembolsos() {
        try {
            int n = reembolsos.reprocessar();
            if (n > 0) {
                log.info("{} reembolso(s) retentado(s).", n);
            }
        } catch (RuntimeException e) {
            log.error("Reprocessamento de reembolsos falhou; tentara de novo no proximo ciclo.", e);
        }
    }
}
