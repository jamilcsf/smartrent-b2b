package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regras do nome de exibicao (visivel a outros usuarios). */
class NomeExibicaoValidadorTest {

    private final NomeExibicaoValidador validador =
            new NomeExibicaoValidador(new MessageFilterService(ChatProperties.padrao()));

    private void recusa(String nome) {
        assertThrows(IllegalArgumentException.class, () -> validador.validar(nome), "deveria recusar: " + nome);
    }

    @Test
    @DisplayName("CT441 - Aceita nomes comuns com acento, numero e pontuacao basica; normaliza espacos")
    void aceitaNomesValidos() {
        assertEquals("Ana Beatriz Rocha", validador.validar("  Ana   Beatriz  Rocha "));
        assertEquals("João D'Ávila-Silva", validador.validar("João D'Ávila-Silva"));
        assertEquals("Casa do Zé 2", validador.validar("Casa do Zé 2"));
        assertEquals("Jr. Souza", validador.validar("Jr. Souza"));
    }

    @Test
    @DisplayName("CT442 - Recusa nome vazio, curto demais, longo demais ou so com numeros")
    void recusaTamanhoEForma() {
        recusa(null);
        recusa("   ");
        recusa("A");
        recusa("a".repeat(61));
        recusa("12345");
        assertEquals(60, validador.validar("a".repeat(60)).length());
    }

    @Test
    @DisplayName("CT443 - Recusa HTML e caracteres de controle (nada de tags no nome)")
    void recusaHtml() {
        recusa("<script>alert(1)</script>");
        recusa("Ana <b>Rocha</b>");
        recusa("Ana\u0000Rocha");
        recusa("Ana \"Rocha\" & Cia");
    }

    @Test
    @DisplayName("CT444 - Recusa telefone, e-mail, link e termo ofensivo (regras do filtro do SmartChat)")
    void recusaContatoELinkEOfensa() {
        recusa("Ana 48 99999-1234");
        recusa("ana@exemplo.com");
        recusa("Ana www.exemplo.com.br");
        recusa("Fale comigo wa.me/5548999991234");
        recusa("Ana arroba gmail");
    }
}
