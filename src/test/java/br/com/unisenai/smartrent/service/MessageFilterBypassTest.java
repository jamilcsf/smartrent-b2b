package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.service.MessageFilterService.Categoria;
import br.com.unisenai.smartrent.service.MessageFilterService.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bypass do filtro de contatos confirmados na auditoria (Unicode, caracteres invisiveis, letras espacadas,
 * numeros misturados com palavras, mensageiros isolados) e os falsos positivos que NAO podem ser mascarados.
 * Os casos usam escapes \\u para nao depender da codificacao do arquivo.
 */
class MessageFilterBypassTest {

    private final MessageFilterService filtro = new MessageFilterService(ChatProperties.padrao());

    private static final String KEYCAP = "\uFE0F\u20E3";

    private Resultado f(String texto) {
        return filtro.filtrar(texto);
    }

    private static String keycaps(String digitos) {
        StringBuilder sb = new StringBuilder();
        for (char c : digitos.toCharArray()) {
            sb.append(c).append(KEYCAP);
        }
        return sb.toString();
    }

    @ParameterizedTest(name = "telefone {index}")
    @DisplayName("CT530 - Telefone com digitos fullwidth, arabe-indicos, emoji, matematicos, invisiveis e espacos especiais e borrado")
    @ValueSource(strings = {
            "\uFF14\uFF18\uFF19\uFF19\uFF19\uFF19\uFF19\uFF10\uFF10\uFF10\uFF10",            // fullwidth
            "\u0664\u0668\u0669\u0669\u0669\u0669\u0669\u0660\u0660\u0660\u0660",            // arabe-indicos
            "\uD835\uDFD2\uD835\uDFD6\uD835\uDFD7\uD835\uDFD7\uD835\uDFD7\uD835\uDFD7\uD835\uDFD7\uD835\uDFCE\uD835\uDFCE\uD835\uDFCE\uD835\uDFCE", // matematicos
            "48 99999\u200B0000",                  // zero-width space
            "4\u200B8 9\u200B9999-0000",
            "48 99999\u00AD0000",                  // soft hyphen
            "48\u00A099999-0000",                  // espaco sem quebra
            "48\u200399999\u20030000",             // em space
            "(48)\u200999999\u20090000",           // thin space
            "48\u3164 99999 0000",                 // Hangul filler
            "me liga \u202E48 99999 0000"          // controle bidi
    })
    void telefonesUnicode(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.TELEFONE), texto + " -> " + r.texto());
        assertFalse(r.texto().contains("9999"), r.texto());
    }

    @Test
    @DisplayName("CT531 - Telefone em digitos-emoji (keycap) e borrado")
    void telefoneKeycap() {
        Resultado r = f("me chama " + keycaps("48999990000"));
        assertTrue(r.categorias().contains(Categoria.TELEFONE), r.texto());
    }

    @ParameterizedTest(name = "misto: {0}")
    @DisplayName("CT532 - Telefone misturando numeros por extenso e digitos e borrado")
    @ValueSource(strings = {
            "cinco cinco quatro oito 9 9 9 9 9 0 0 0 0",
            "quatro8 nove9 nove9 nove9 zero zero zero zero",
            "48 nove nove nove nove 0000",
            "meu numero: quarenta e oito 99999 zero zero zero zero"
    })
    void telefonesMistos(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.TELEFONE), texto + " -> " + r.texto());
    }

    @ParameterizedTest(name = "email/link: {0}")
    @DisplayName("CT533 - E-mail e link com letras espacadas, pontos ideograficos/fullwidth e invisiveis sao borrados")
    @CsvSource(delimiter = ';', value = {
            "j o a o @ g m a i l . c o m;EMAIL",
            "j o a o arroba g m a i l ponto com;EMAIL",
            "joao@gmail\u3002com;EMAIL",
            "joao\uFF20gmail\uFF0Ecom;EMAIL",
            "jo\u200Bao@gm\u200Bail.com;EMAIL",
            "g m a i l ponto com;LINK",
            "exemplo\uFF0Ecom\uFF0Ebr;LINK",
            "exem\u200Bplo.com.br;LINK",
            "e x e m p l o . c o m . b r;LINK",
            "https://ex\uFF41mplo.com;LINK"
    })
    void emailsELinks(String texto, Categoria esperada) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(esperada), texto + " -> " + r.categorias() + " " + r.texto());
    }

    @ParameterizedTest(name = "mensageiro: {0}")
    @DisplayName("CT534 - Mensageiros e redes sociais citados sozinhos sao borrados (CONTATO_EXTERNO)")
    @ValueSource(strings = {
            "me chama no whats", "meu zap eh esse", "fala comigo no WhatsApp", "wpp?", "me acha no telegram",
            "meu insta eh fulano", "w h a t s a p p", "chama no zapzap", "meu facebook", "tem discord?",
            "W\u200BHATSAPP"
    })
    void mensageiros(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.CONTATO_EXTERNO), texto + " -> " + r.texto());
        assertTrue(r.alterado(), texto);
    }

    @ParameterizedTest(name = "legitimo: {0}")
    @DisplayName("CT535 - Falsos positivos: precos, datas, hospedes, CEP, codigos e palavras parecidas NAO sao mascarados")
    @ValueSource(strings = {
            "R$ 1.200,00 por 3 noites",
            "R$ 10.000.000,00 de entrada",
            "R\uFF04 \uFF11.\uFF12\uFF10\uFF10,\uFF10\uFF10 por 3 noites",
            "check-in 12/10/2026 as 14:00 para 4 hospedes",
            "chegamos dia 12 de outubro, 2 dias, 3 pessoas",
            "reserva IMV-0012 com 2 quartos",
            "cep 88010-400",
            "somos 4 adultos e 2 criancas",
            "apartamento 101, quarto dois, 3 pessoas",
            "valor total R$ 2.500,00 em 10x",
            "o zapateado e a instalacao do ar condicionado",
            "whatsoever nao importa",
            "quero 2 camas, 1 banheiro, 3 vagas e 80 m2",
            "disputa pelo computador da sala",
            "Obrigado! Chegamos as 15h30 e saimos dia 20/12."
    })
    void falsosPositivos(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().isEmpty(), texto + " -> " + r.categorias() + " " + r.texto());
        assertFalse(r.alterado(), texto);
    }

    @Test
    @DisplayName("CT536 - 'pix' continua so como alerta: nada e borrado, mas a mensagem e sinalizada")
    void pixSoAlerta() {
        for (String t : new String[]{"me manda o pix direto", "faz um pix por fora", "meu pix e o email"}) {
            Resultado r = f(t);
            assertTrue(r.categorias().contains(Categoria.SUSPEITA_FRAUDE), t);
            assertFalse(r.alterado(), t);
        }
        assertFalse(f("posso pagar com pix na plataforma?").alterado());
    }

    @Test
    @DisplayName("CT537 - O texto devolvido nao contem o contato original nem os caracteres invisiveis")
    void saidaSemOriginal() {
        Resultado r = f("ligue 48\u200B 99999\u200B-0000 ou joao\uFF20gmail\uFF0Ecom");
        assertFalse(r.texto().contains("9999"));
        assertFalse(r.texto().toLowerCase().contains("gmail"));
        assertFalse(r.texto().contains("\u200B"));
    }
}
