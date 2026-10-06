package br.com.unisenai.smartrent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Politica "nenhum HTML" (OWASP Java HTML Sanitizer) sem estragar texto normal. */
class SanitizadorTest {

    @ParameterizedTest(name = "ataque: {0}")
    @DisplayName("CT1070 - Marcacao ativa nao sobrevive (script, eventos, iframe, svg, javascript:, entidades, HTML quebrado)")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<img src=x onerror=alert(1)",
            "<svg/onload=alert(1)>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "<a href=\"javascript:alert(1)\">clique</a>",
            "<body onload=alert(1)>",
            "<style>*{background:url(javascript:alert(1))}</style>",
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "&#60;img src=x onerror=alert(1)&#62;",
            "<<script>alert(1)//<</script>",
            "<scr<script>ipt>alert(1)</scr</script>ipt>",
            "<math><mi//xlink:href=\"data:x,<script>alert(1)</script>\">"
    })
    void marcacaoAtiva(String entrada) {
        for (String saida : new String[]{Sanitizador.texto(entrada), Sanitizador.linha(entrada)}) {
            String s = saida.toLowerCase();
            assertFalse(s.contains("<script") || s.contains("<img") || s.contains("<svg") || s.contains("<iframe")
                    || s.contains("<a ") || s.contains("<body") || s.contains("<style") || s.contains("onerror=")
                    && s.contains("<"), entrada + " -> " + saida);
            assertFalse(s.matches("(?s).*<[a-z/!?].*"), entrada + " -> " + saida);
        }
    }

    @Test
    @DisplayName("CT1071 - O conteudo de script e style some junto com a tag")
    void conteudoDeScript() {
        assertEquals("oi", Sanitizador.texto("<script>roubar()</script>oi"));
        assertEquals("ok", Sanitizador.linha("<style>body{display:none}</style>ok"));
    }

    @ParameterizedTest(name = "preservado: {0}")
    @DisplayName("CT1072 - Texto normal fica intacto: precos, '<' e '&' soltos, acentos, emoji, paragrafos")
    @CsvSource(delimiter = ';', value = {
            "Preco < R$ 100 e > R$ 50;Preco < R$ 100 e > R$ 50",
            "Cafe & pao;Cafe & pao",
            "Olá, ação não é São João;Olá, ação não é São João",
            "2 quartos <3 camas>;2 quartos <3 camas>",
            "5 > 3 e 2 < 4;5 > 3 e 2 < 4"
    })
    void textoNormal(String entrada, String esperado) {
        assertEquals(esperado, Sanitizador.linha(entrada));
    }

    @Test
    @DisplayName("CT1073 - Paragrafos, emoji e controle: preserva paragrafo, remove caracteres de controle, nulo continua nulo")
    void formatacao() {
        assertEquals("linha 1\n\nlinha 2 \uD83D\uDE00", Sanitizador.texto("linha 1\r\n\r\n\r\n\r\nlinha\u0000  2   \uD83D\uDE00"));
        assertEquals("a b", Sanitizador.linha("a\nb"));
        assertNull(Sanitizador.texto(null));
        assertNull(Sanitizador.linha(null));
        assertEquals("", Sanitizador.texto("   "));
    }
}
