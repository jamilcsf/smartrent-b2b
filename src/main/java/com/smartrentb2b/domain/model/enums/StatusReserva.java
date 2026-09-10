package com.smartrentb2b.domain.model.enums;

/**
 * Ciclo de vida de uma reserva.
 * IMPORTANTE: reservas em status {@link #CANCELADA} são ignoradas pela
 * verificação de conflito de datas em {@code ReservaRepository}.
 */
public enum StatusReserva {
    PENDENTE,
    CONFIRMADA,
    CANCELADA,
    CONCLUIDA
}