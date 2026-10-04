package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Segunda linha de defesa do SmartChat: refaz o que ficou para tras (conversa de
 * reserva confirmada ou mensagem de cancelamento ausente), por exemplo quando o
 * chat falhou logo depois do commit da reserva. Idempotente: nada e duplicado.
 */
@Component
public class ChatReconciliador {

    private static final Logger log = LoggerFactory.getLogger(ChatReconciliador.class);

    private final ReservaRepository reservas;
    private final SmartChatService chat;
    private final TransactionTemplate transacao;

    public ChatReconciliador(ReservaRepository reservas, SmartChatService chat, PlatformTransactionManager tm) {
        this.reservas = reservas;
        this.chat = chat;
        this.transacao = new TransactionTemplate(tm);
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.chat-reconciliacao-intervalo-ms:300000}", initialDelay = 90_000)
    public void agendado() {
        try {
            int n = reconciliar();
            if (n > 0) {
                log.info("SmartChat: {} item(ns) reconciliado(s).", n);
            }
        } catch (RuntimeException e) {
            log.error("Reconciliacao do SmartChat falhou.", e);
        }
    }

    /** Devolve quantos itens foram refeitos. */
    public int reconciliar() {
        int n = 0;
        for (Reserva r : transacao.execute(s -> reservas.findConfirmadasSemConversa())) {
            try {
                transacao.executeWithoutResult(s -> chat.garantirConversaDaReserva(r.getId()));
                n++;
            } catch (RuntimeException e) {
                log.warn("Reconciliacao do SmartChat falhou para a reserva {}", r.getId(), e);
            }
        }
        for (Reserva r : transacao.execute(s -> reservas.findCanceladasSemMensagem())) {
            try {
                transacao.executeWithoutResult(s -> chat.registrarCancelamento(r.getId()));
                n++;
            } catch (RuntimeException e) {
                log.warn("Reconciliacao do cancelamento falhou para a reserva {}", r.getId(), e);
            }
        }
        return n;
    }
}
