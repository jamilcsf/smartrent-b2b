package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executa algo (normalmente enviar e-mail) somente depois que a transacao atual confirma, e
 * nunca deixa uma falha desse passo desfazer ou mascarar a acao ja concluida. Sem transacao
 * ativa, executa na hora.
 */
public final class AposCommit {

    private static final Logger log = LoggerFactory.getLogger(AposCommit.class);

    private AposCommit() {
    }

    public static void executar(String descricao, Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rodar(descricao, acao);
                }
            });
        } else {
            rodar(descricao, acao);
        }
    }

    private static void rodar(String descricao, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            log.warn("Falha ao executar {} depois do commit: {}", descricao, e.getClass().getSimpleName());
        }
    }
}
