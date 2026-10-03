package br.com.unisenai.smartrent.model.enums;

/**
 * Situacao de uma reserva. Os tres estados de cancelamento dizem quem cancelou
 * e se houve reembolso; o detalhe (regra aplicada, motivo, valor) fica no
 * registro de cancelamento e no reembolso. Reserva pendente cancelada ou expirada
 * (sem cobranca) usa CANCELADA_SEM_REEMBOLSO com regra PENDENTE_SEM_COBRANCA.
 */
public enum StatusReserva {
    PENDENTE,
    CONFIRMADA,
    CANCELADA_COM_REEMBOLSO,
    CANCELADA_SEM_REEMBOLSO,
    CANCELADA_PELO_GESTOR,
    CONCLUIDA;

    /** Verdadeiro para qualquer estado de cancelamento: libera as datas na hora. */
    public boolean cancelada() {
        return this == CANCELADA_COM_REEMBOLSO || this == CANCELADA_SEM_REEMBOLSO || this == CANCELADA_PELO_GESTOR;
    }

    /** Estados que contam como ocupacao (pendente ainda nao e ocupacao nem receita). */
    public boolean ocupaDatas() {
        return this == CONFIRMADA || this == CONCLUIDA;
    }
}
