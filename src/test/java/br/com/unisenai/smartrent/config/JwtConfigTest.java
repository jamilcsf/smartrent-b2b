package br.com.unisenai.smartrent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** O segredo do JWT nunca cai no valor publico do repositorio fora de dev/test. */
class JwtConfigTest {

    private static final String BOM = "um-segredo-aleatorio-com-mais-de-trinta-e-dois-bytes";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("CT500 - Producao sem JWT_SECRET: a aplicacao nao sobe")
    void producaoSemSegredo(String segredo) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> JwtConfig.resolver(segredo, false));
        assertTrue(e.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("CT501 - Producao com o segredo publico de desenvolvimento: recusa")
    void producaoComSegredoPublico() {
        assertThrows(IllegalStateException.class, () -> JwtConfig.resolver(JwtConfig.SEGREDO_DESENVOLVIMENTO, false));
    }

    @Test
    @DisplayName("CT502 - Segredo curto (menos de 32 bytes): recusa e a mensagem nao traz o valor")
    void segredoCurto() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> JwtConfig.resolver("curto-demais", false));
        assertFalse(e.getMessage().contains("curto-demais"));
    }

    @Test
    @DisplayName("CT503 - Producao com segredo forte: usa exatamente o informado")
    void producaoComSegredoForte() {
        assertEquals(BOM, JwtConfig.resolver(BOM, false));
    }

    @Test
    @DisplayName("CT504 - Dev/test sem segredo: usa o segredo de desenvolvimento; com segredo, o informado")
    void desenvolvimento() {
        assertEquals(JwtConfig.SEGREDO_DESENVOLVIMENTO, JwtConfig.resolver(null, true));
        assertEquals(BOM, JwtConfig.resolver(BOM, true));
    }
}
