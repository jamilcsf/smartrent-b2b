package br.com.unisenai.smartrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Garantias estaticas do front do SmartChat (nao ha framework de teste de interface). */
class SmartChatEstaticoTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static String ler(String arquivo) throws IOException {
        return Files.readString(STATIC.resolve(arquivo));
    }

    @Test
    @DisplayName("Aviso fixo: texto exato, role=note, fora da area que rola e sem botao de fechar")
    void avisoFixo() throws IOException {
        String html = ler("smartchat.html");
        int ini = html.indexOf("id=\"avisoSeguranca\"");
        assertTrue(ini > 0);
        int fim = html.indexOf("</p>", ini);
        String faixa = html.substring(ini, fim);
        assertTrue(faixa.contains("role=\"note\""));
        assertTrue(faixa.contains("Para sua segurança, nunca faça pagamentos ou compartilhe contatos fora da nossa plataforma."));
        assertFalse(faixa.contains("<button"), "nao pode ser dispensada");
        assertTrue(html.indexOf("id=\"avisoSeguranca\"") < html.indexOf("id=\"mensagens\""), "acima da lista");
        assertFalse(ler("js/smartchat.js").contains("avisoSeguranca"), "o JS nao a esconde nem remove");
    }

    @Test
    @DisplayName("E-mail verificado: aviso com reenvio no chat, selo no perfil e pagina do link sem token na URL; nunca 'identidade verificada'")
    void verificacaoDeEmail() throws IOException {
        String html = ler("smartchat.html");
        assertTrue(html.contains("id=\"avisoVerificacao\"") && html.contains("id=\"btnReenviarVerificacao\""));
        String js = ler("js/smartchat.js");
        assertTrue(js.contains("E-mail verificado"), "selo");
        assertTrue(js.contains("/api/perfil/email/verificacao/reenviar"));
        String pagina = ler("verificar-email.html");
        assertTrue(pagina.contains("name=\"referrer\" content=\"no-referrer\""));
        assertTrue(pagina.contains("history.replaceState"), "tira o token da barra de enderecos");
        assertTrue(pagina.contains("/api/perfil/email/verificar"));
        try (java.util.stream.Stream<Path> arquivos = Files.walk(Path.of("src/main"))) {
            for (Path p : (Iterable<Path>) arquivos.filter(Files::isRegularFile)::iterator) {
                String nome = p.getFileName().toString();
                if (nome.endsWith(".java") || nome.endsWith(".js") || nome.endsWith(".html") || nome.endsWith(".properties")) {
                    assertFalse(Files.readString(p).toLowerCase().contains("identidade verificada"), p.toString());
                }
            }
        }
    }
}
