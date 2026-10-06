package br.com.unisenai.smartrent.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** IllegalArgumentException de biblioteca nao pode vazar detalhe interno; a do proprio codigo mantem a mensagem ao usuario. */
class MensagemDeErroSeguraTest {

    @Test
    @DisplayName("CT1093 - IAE lancada pelo codigo da aplicacao mantem a mensagem")
    void doProprioCodigo() {
        assertEquals("Informe o motivo.", TratadorDeErros.mensagemSegura(new IllegalArgumentException("Informe o motivo.")));
    }

    @Test
    @DisplayName("CT1094 - IAE do JDK (UUID, Base64, Path) vira mensagem generica")
    void daBiblioteca() {
        IllegalArgumentException jdk = null;
        try {
            UUID.fromString("isto-nao-e-uuid");
        } catch (IllegalArgumentException e) {
            jdk = e;
        }
        assertEquals("Requisição inválida.", TratadorDeErros.mensagemSegura(jdk));
    }
}
