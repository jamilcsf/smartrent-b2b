package br.com.unisenai.smartrent.security;

import br.com.unisenai.smartrent.config.ChatSegurancaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class HmacTextoTest {

    private static byte[] chave(int semente) {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (semente + i);
        }
        return b;
    }

    private final HmacTexto hmac = new HmacTexto(chave(1));

    @Test
    @DisplayName("Normalizacao ignora caixa, acento, espacos e pontuacao")
    void normalizacao() {
        assertEquals("ola tudo bem", HmacTexto.normalizar("Olá,  TUDO   bem?!"));
        assertEquals(hmac.resumoDe("Olá, tudo bem?"), hmac.resumoDe("  ola   TUDO bem "));
        assertNotEquals(hmac.resumoDe("Olá, tudo bem?"), hmac.resumoDe("Olá, tudo mal?"));
        assertEquals("", HmacTexto.normalizar(null));
    }

    @Test
    @DisplayName("O resumo e hexadecimal de 64 caracteres, nao contem o texto e depende da chave")
    void resumo() {
        String r = hmac.resumoDe("segredo da mensagem");
        assertTrue(r.matches("[0-9a-f]{64}"));
        assertFalse(r.contains("segredo"));
        assertNotEquals(r, new HmacTexto(chave(9)).resumoDe("segredo da mensagem"));
        assertEquals(r, new HmacTexto(chave(1)).resumoDe("segredo da mensagem"), "determinístico");
    }

    @Test
    @DisplayName("Chave Base64 precisa ter pelo menos 32 bytes e a mensagem de erro nao ecoa o valor")
    void chaves() {
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> HmacTexto.deBase64(curta));
        assertFalse(e.getMessage().contains(curta));
        assertThrows(IllegalArgumentException.class, () -> HmacTexto.deBase64(null));
        assertThrows(IllegalArgumentException.class, () -> HmacTexto.deBase64("%%%"));
        assertNotNull(HmacTexto.deBase64(Base64.getEncoder().encodeToString(chave(3))));
        assertNotNull(HmacTexto.deBase64(Base64.getEncoder().encodeToString(new byte[64])));
    }

    @Test
    @DisplayName("O toString das propriedades de seguranca nao revela a chave")
    void toStringSemChave() {
        String k = Base64.getEncoder().encodeToString(chave(5));
        assertFalse(new ChatSegurancaProperties(k, 5, 3, 10, 7, 3, 60, 60).toString().contains(k));
    }
}
