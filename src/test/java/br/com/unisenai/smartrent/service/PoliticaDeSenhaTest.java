package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Politica da nova senha: tamanho, lista local de senhas comuns, repeticao e e-mail. */
class PoliticaDeSenhaTest {

    private final PoliticaDeSenha politica = new PoliticaDeSenha(PerfilProperties.padrao());

    private String recusa(String senha) {
        return assertThrows(IllegalArgumentException.class, () -> politica.validar(senha, "ana.rocha@smartrent.dev"),
                "deveria recusar: " + senha).getMessage();
    }

    @Test
    @DisplayName("CT457 - Aceita senha forte e frase-senha longa")
    void aceitaSenhasBoas() {
        assertDoesNotThrow(() -> politica.validar("Cavalo-Bateria-Grampo-26", "ana@smartrent.dev"));
        assertDoesNotThrow(() -> politica.validar("x7Q!mZ4#pL9vR2", "ana@smartrent.dev"));
        assertDoesNotThrow(() -> politica.validar("uma frase bem longa e unica", "ana@smartrent.dev"));
    }

    @Test
    @DisplayName("CT458 - Recusa senha curta (menos de 10) e longa demais para o BCrypt (mais de 72 bytes)")
    void recusaTamanho() {
        assertTrue(recusa("Abc!1234").contains("10"));
        assertTrue(recusa(null).contains("10"));
        assertTrue(recusa("a1B!".repeat(19)).contains("72"));
        assertDoesNotThrow(() -> politica.validar("a1B!c2D@".repeat(9), "ana@smartrent.dev")); // 72 bytes exatos
    }

    @Test
    @DisplayName("CT459 - Recusa senhas comuns/vazadas mesmo com maiusculas, acentos, simbolos, trocas tipo 4/a e numeros no fim")
    void recusaComuns() {
        for (String comum : new String[]{"1234567890", "qwertyuiop", "Password123", "P@ssw0rd2024", "senhasegura123",
                "SenhaSegura123", "S3nh@123456", "minhasenha2025", "Brasil@2014!!", "iloveyou123", "ÀBCDEFGHIJ"}) {
            assertTrue(recusa(comum).toLowerCase().contains("comum"), comum);
        }
    }

    @Test
    @DisplayName("CT460 - Recusa senha repetitiva e senha que contem o e-mail da conta")
    void recusaRepetitivaEEmail() {
        assertTrue(recusa("aaaaaaaaaaaa").contains("repetitiva"));
        assertTrue(recusa("abababababab").contains("repetitiva"));
        assertTrue(recusa("xx-ana.rocha-99!K").contains("e-mail"));
        assertTrue(recusa("ANA.ROCHA#2026#xyz").contains("e-mail"));
    }
}
