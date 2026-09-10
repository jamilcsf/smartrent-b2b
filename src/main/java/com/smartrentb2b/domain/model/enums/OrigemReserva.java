package com.smartrentb2b.domain.model.enums;

/**
 * Canal de origem da reserva. Reservas oriundas de OTAs externas (Airbnb,
 * Booking) hoje são cadastradas manualmente pelo anfitrião no MVP; a
 * sincronização bidirecional via iCal é Visão Futura (V2).
 */
public enum OrigemReserva {
    DIRETA,
    AIRBNB,
    BOOKING,
    OUTRA_OTA
}