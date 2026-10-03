package br.com.unisenai.smartrent.service;

/**
 * Publicado quando uma reserva passa a CONFIRMADA. Quem reage (SmartChat,
 * notificacoes) o faz depois do commit e sem poder desfazer a confirmacao.
 */
public record ReservaConfirmadaEvent(Long reservaId) {
}
