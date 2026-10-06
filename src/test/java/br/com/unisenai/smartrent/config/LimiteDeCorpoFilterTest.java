package br.com.unisenai.smartrent.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Corpo JSON sem teto deixaria qualquer endpoint receber centenas de MB. */
class LimiteDeCorpoFilterTest {

    private final LimiteDeCorpoFilter filtro = new LimiteDeCorpoFilter(100);

    private MockHttpServletRequest requisicao(String tipo, byte[] corpo) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/x");
        r.setContentType(tipo);
        r.setContent(corpo);
        return r;
    }

    @Test
    @DisplayName("CT1090 - Content-Length acima do teto: 413 sem chamar o restante da cadeia")
    void acimaDoTeto() throws ServletException, IOException {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(requisicao("application/json", new byte[101]), resposta, cadeia);
        assertEquals(413, resposta.getStatus());
        assertNull(cadeia.getRequest(), "a cadeia nao foi chamada");
        assertTrue(resposta.getContentAsString(StandardCharsets.UTF_8).contains("grande demais"));
    }

    @Test
    @DisplayName("CT1091 - Dentro do teto passa; corpo sem Content-Length que estoura o teto na leitura falha")
    void dentroEEstouroNaLeitura() throws Exception {
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(requisicao("application/json", new byte[100]), new MockHttpServletResponse(), cadeia);
        assertNotNull(cadeia.getRequest());
        assertEquals(100, cadeia.getRequest().getInputStream().readAllBytes().length);

        MockHttpServletRequest semTamanho = new MockHttpServletRequest("POST", "/api/x") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked
            }
        };
        semTamanho.setContentType("application/json");
        semTamanho.setContent(new byte[500]);
        MockFilterChain cadeia2 = new MockFilterChain();
        filtro.doFilter(semTamanho, new MockHttpServletResponse(), cadeia2);
        assertThrows(IOException.class, () -> cadeia2.getRequest().getInputStream().readAllBytes());
    }

    @Test
    @DisplayName("CT1092 - Upload (multipart) e outros tipos nao sao limitados por este filtro")
    void outrosTipos() throws Exception {
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(requisicao("multipart/form-data; boundary=x", new byte[5000]), new MockHttpServletResponse(), cadeia);
        assertNotNull(cadeia.getRequest());
    }
}
