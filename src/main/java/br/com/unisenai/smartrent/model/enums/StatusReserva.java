package br.com.unisenai.smartrent.model.enums;

/** Situacao de uma reserva no seu ciclo de vida. */
public enum StatusReserva {
    PENDENTE,
    CONFIRMADA,
    CANCELADA,
    CONCLUIDA;

    /** Verdadeiro para qualquer estado de cancelamento. */
    public boolean cancelada() {
        return this == CANCELADA;
    }

    /** Estados que contam como ocupacao (pendente ainda nao e ocupacao nem receita). */
    public boolean ocupaDatas() {
        return this == CONFIRMADA || this == CONCLUIDA;
    }
}
