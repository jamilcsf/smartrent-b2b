package br.com.unisenai.smartrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garantias estaticas do front do perfil (nao ha framework de teste de interface no projeto):
 * o cabecalho e um unico componente compartilhado, a regiao do usuario leva ao perfil de forma
 * acessivel, o nome nunca vira HTML e as paginas de link por e-mail tiram o token da URL.
 */
class PerfilEstaticoTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static String ler(String arquivo) throws IOException {
        return Files.readString(STATIC.resolve(arquivo));
    }

    @Test
    @DisplayName("CT505 - Cabecalho: toda pagina com area do usuario usa o componente compartilhado; avatar+nome sao um link acessivel para o perfil e o Sair e separado")
    void cabecalhoLevaAoPerfil() throws IOException {
        String componente = ler("js/header-auth.js");
        assertTrue(componente.contains("link.href = '/perfil.html'"));
        assertTrue(componente.contains("'Abrir meu perfil'"), "aria-label do link");
        assertTrue(componente.contains("focus-visible:outline"), "foco visivel para teclado");
        assertTrue(componente.contains("hover:bg-slate-100"), "estado de hover");
        assertTrue(componente.contains("btnSair"), "o Sair continua existindo");
        // O link e <a> (Enter nativo) e o botao Sair NAO esta dentro dele.
        assertTrue(componente.contains("document.createElement('a')"));
        assertTrue(componente.indexOf("caixa.appendChild(link)") < componente.indexOf("caixa.appendChild(sair)"));
        // foto ou iniciais; nome sempre por textContent, nunca por innerHTML
        assertTrue(componente.contains("nome.textContent = u.nome"));
        assertTrue(componente.contains("/^\\/api\\/perfil\\/foto\\//"), "so aceita foto servida pelo proprio perfil");
        assertFalse(componente.matches("(?s).*innerHTML\\s*=\\s*[^;]*u\\.nome.*"), "nome nunca entra em innerHTML");

        try (Stream<Path> paginas = Files.list(STATIC)) {
            List<Path> comArea = paginas.filter(p -> p.toString().endsWith(".html")).filter(p -> {
                try {
                    return Files.readString(p).contains("id=\"areaAuth\"");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertFalse(comArea.isEmpty());
            for (Path p : comArea) {
                assertTrue(Files.readString(p).contains("header-auth.js"), p.getFileName() + " deve usar o componente compartilhado");
            }
        }
    }

    @Test
    @DisplayName("CT506 - Pagina do perfil: exige login (volta depois), nao recebe id de usuario e tem as tres secoes")
    void paginaDoPerfil() throws IOException {
        String html = ler("perfil.html");
        for (String secao : new String[]{"Dados da conta", "Segurança", "Privacidade", "Solicitar exclusão de dados"}) {
            assertTrue(html.contains(secao), secao);
        }
        String js = ler("js/perfil.js");
        assertTrue(js.contains("Api.urlDeLogin(Auth.rotaAtual())"), "visitante vai ao login e volta");
        assertFalse(js.toLowerCase().contains("usuarioid") || js.contains("/api/perfil/' +") || js.contains("/api/perfil/\" +"),
                "nenhuma chamada monta URL com id de usuario");
        assertTrue(js.contains("/api/perfil/senha") && js.contains("/api/perfil/email") && js.contains("/api/perfil/exclusao-dados"));
    }

    @Test
    @DisplayName("CT507 - Paginas de link do e-mail: token sai da URL, nao vaza por Referer e o cancelamento exige o clique (leitor de e-mail que so abre o link nao cancela)")
    void paginasDeLinkDoEmail() throws IOException {
        for (String pagina : new String[]{"confirmar-email.html", "cancelar-exclusao.html"}) {
            String html = ler(pagina);
            assertTrue(html.contains("name=\"referrer\" content=\"no-referrer\""), pagina);
            assertTrue(html.contains("history.replaceState"), pagina);
        }
        String cancelar = ler("cancelar-exclusao.html");
        assertTrue(cancelar.contains("addEventListener('click'"), "o POST so sai depois do clique");
        assertTrue(cancelar.indexOf("addEventListener('click'") < cancelar.indexOf("Api.post("));
    }
}
