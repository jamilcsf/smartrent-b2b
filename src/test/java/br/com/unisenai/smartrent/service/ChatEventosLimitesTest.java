package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/** Um usuario nao pode esgotar memoria e threads abrindo conexoes SSE ou pedindo tickets sem parar. */
class ChatEventosLimitesTest {

    private final ChatEventos eventos = new ChatEventos(Clock.systemUTC());

    @Test
    @DisplayName("CT1100 - Tickets pendentes por usuario tem teto; os mais antigos caem e os novos funcionam")
    void tetoDeTickets() {
        String primeiro = eventos.emitirTicket(7L);
        for (int i = 0; i < 20; i++) {
            eventos.emitirTicket(7L);
        }
        assertEquals(ChatEventos.MAX_POR_USUARIO, eventos.ticketsPendentes(7L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> eventos.abrir(primeiro));
        assertEquals(1, eventos.ticketsPendentes(8L) + 1, "outro usuario nao e afetado");
    }

    @Test
    @DisplayName("CT1101 - Conexoes SSE por usuario tem teto; a mais antiga cai ao passar dele")
    void tetoDeConexoes() {
        for (int i = 0; i < 12; i++) {
            eventos.abrir(eventos.emitirTicket(9L));
        }
        assertEquals(ChatEventos.MAX_POR_USUARIO, eventos.conexoesAbertas(9L));
        assertTrue(eventos.conectado(9L));
        assertFalse(eventos.conectado(10L));
    }

    @Test
    @DisplayName("CT1102 - Ticket e de uso unico")
    void usoUnico() {
        String t = eventos.emitirTicket(11L);
        assertNotNull(eventos.abrir(t));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> eventos.abrir(t));
    }
}
