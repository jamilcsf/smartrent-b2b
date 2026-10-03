package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liga reservas ao SmartChat DEPOIS do commit da reserva, em transacao propria
 * (nova): se o chat falhar, o erro e registrado e a reconciliacao ({@link ChatReconciliador},
 * ou a abertura da conversa pela pagina da reserva) refaz, mas a confirmacao ou o
 * cancelamento ja estao gravados.
 */
@Component
public class ChatReservaListener {

    private static final Logger log = LoggerFactory.getLogger(ChatReservaListener.class);

    private final SmartChatService chat;
    private final TransactionTemplate novaTransacao;

    public ChatReservaListener(SmartChatService chat, PlatformTransactionManager tm) {
        this.chat = chat;
        this.novaTransacao = new TransactionTemplate(tm);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmar(ReservaConfirmadaEvent evento) {
        try {
            novaTransacao.executeWithoutResult(s -> chat.garantirConversaDaReserva(evento.reservaId()));
        } catch (RuntimeException e) {
            log.error("Falha ao criar a conversa da reserva {}; a reconciliacao tentara de novo.", evento.reservaId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCancelar(ReservaCanceladaEvent evento) {
        try {
            novaTransacao.executeWithoutResult(s -> chat.registrarCancelamento(evento.reservaId()));
        } catch (RuntimeException e) {
            log.error("Falha ao registrar o cancelamento da reserva {} no chat; a reconciliacao tentara de novo.", evento.reservaId(), e);
        }
    }
}
