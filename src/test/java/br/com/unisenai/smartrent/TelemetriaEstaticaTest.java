package br.com.unisenai.smartrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Garantias estaticas do coletor e do painel de admin (nao ha framework de teste de interface no projeto). */
class TelemetriaEstaticaTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static String ler(String arquivo) throws IOException {
        return Files.readString(STATIC.resolve(arquivo));
    }

    @Test
    @DisplayName("CT1121 - Toda pagina do site carrega o coletor, exceto a de redirecionamento (index) e as areas de admin (painel e moderacao)")
    void paginasCarregamOColetor() throws IOException {
        try (Stream<Path> arquivos = Files.list(STATIC)) {
            List<Path> paginas = arquivos.filter(p -> p.toString().endsWith(".html")).toList();
            assertFalse(paginas.isEmpty());
            for (Path p : paginas) {
                String nome = p.getFileName().toString();
                boolean tem = Files.readString(p).contains("src=\"/js/telemetria.js\"");
                if (nome.equals("index.html") || nome.equals("admin.html") || nome.equals("moderacao.html")) {
                    assertFalse(tem, nome + " nao deve ser medida");
                } else {
                    assertTrue(tem, nome + " precisa carregar /js/telemetria.js");
                }
            }
        }
    }

    @Test
    @DisplayName("CT1122 - Coletor anonimo: respeita DNT/GPC, nao roda em iframe nem no admin, nao le texto, valor digitado, cookie nem query")
    void coletorAnonimo() throws IOException {
        String js = ler("js/telemetria.js");
        assertTrue(js.contains("doNotTrack"));
        assertTrue(js.contains("globalPrivacyControl"));
        assertTrue(js.contains("global.top !== global.self"), "nao conta a si mesmo dentro do mapa de calor");
        assertTrue(js.contains("(admin|moderacao)\\.html"), "o coletor ignora as areas de admin");
        for (String proibido : List.of("innerText", "textContent", ".value", "document.cookie", "localStorage", "location.search", "location.href")) {
            assertFalse(js.contains(proibido), "o coletor nao pode usar " + proibido);
        }
        assertTrue(js.contains("global.location.pathname"), "so o caminho, sem query");
    }

    @Test
    @DisplayName("CT1123 - Painel de admin: sem innerHTML com dado cru (tudo escapado), iframe do mapa nao interativo e com sandbox")
    void painelSeguro() throws IOException {
        String html = ler("admin.html");
        assertTrue(html.contains("pointer-events-none"), "o admin nao clica dentro da pagina que esta inspecionando");
        assertTrue(html.contains("sandbox=\""));
        assertFalse(html.contains("telemetria.js"));
        String js = ler("js/admin.js");
        assertTrue(js.contains("var esc = global.UI.escapar"));
        // Campos livres do usuario (nome, titulo, hospede) so entram escapados.
        for (String campo : List.of("u.nome", "i.titulo", "r.hospede", "r.imovel", "e.alvo", "i.local", "i.gestor", "u.email")) {
            assertTrue(js.contains("esc(" + campo), campo + " precisa passar por esc()");
        }
        assertFalse(js.contains("eval("));
    }

    @Test
    @DisplayName("CT1138 - Moderacao: todo texto de usuario entra escapado; acoes exigem justificativa; avisos entram como texto, nunca HTML")
    void moderacaoSegura() throws IOException {
        String js = ler("js/moderacao.js") + ler("js/moderacao-acoes.js");
        for (String campo : List.of("u.nome", "d.descricao", "m.texto", "c.texto", "c.assunto", "a.explicacao", "a.efeito", "d.imovel", "u.email", "a.nome", "a.motivo")) {
            assertTrue(js.contains("esc(" + campo), campo + " precisa passar por esc()");
        }
        assertFalse(js.contains("eval("));
        assertTrue(js.contains("Escreva a justificativa") && js.contains("Escreva o motivo"), "decisoes e suspensoes exigem justificativa");
        String avisos = ler("js/comunicados.js");
        assertTrue(avisos.contains("textContent = texto"));
        assertFalse(avisos.contains("innerHTML"), "o texto do aviso nunca vira HTML");
        assertFalse(ler("moderacao.html").contains("telemetria.js"), "a moderacao nao e medida");
        assertTrue(ler("js/navegacao.js").contains("/moderacao.html") && ler("js/navegacao.js").contains("/comunicados.html"));
    }
}
