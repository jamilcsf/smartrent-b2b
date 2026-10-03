package br.com.unisenai.smartrent.service;

/**
 * Publicado quando uma reserva e cancelada (por cliente, gestor ou pelo sistema).
 * O SmartChat registra a mensagem de sistema; a conversa e o historico nunca sao apagados.
 */
public record ReservaCanceladaEvent(Long reservaId) {
}
