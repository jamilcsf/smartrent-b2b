package br.com.unisenai.smartrent.service;

import org.owasp.html.Encoding;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.regex.Pattern;

/**
 * Limpa texto digitado pelo usuario antes de gravar (ADR-008).
 *
 * <p>Politica "nenhum HTML": o OWASP Java HTML Sanitizer remove todos os elementos (e o CONTEUDO de {@code script}
 * e {@code style}), atributos de evento e comentarios, mesmo em HTML malformado. O resultado volta a texto puro
 * (as entidades sao decodificadas) e qualquer marcacao residual e removida. Um "<" solto de uso normal
 * ("preco < 100") e preservado. E defesa em profundidade: o front tambem escapa tudo o que renderiza, mas o dado
 * nao deve chegar ao banco carregando {@code <script>} para o dia em que alguem o consumir sem escapar.
 */
public final class Sanitizador {

    private static final PolicyFactory NENHUM_HTML = new HtmlPolicyBuilder().toFactory();

    /** Inicio de marcacao: "<" seguido de letra, "/", "!" ou "?"; o resto ate o ">" (ou o fim) sai junto. */
    private static final Pattern TAG = Pattern.compile("<[a-zA-Z/!?][^>]*>?");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern ESPACOS = Pattern.compile("[ \\t]+");
    private static final Pattern LINHAS_EM_EXCESSO = Pattern.compile("\\n{3,}");

    private Sanitizador() {
    }

    /** Texto de uma linha: sem tags, sem quebras, espacos colapsados. Nulo continua nulo. */
    public static String linha(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = semHtml(texto);
        limpo = CONTROLE.matcher(limpo).replaceAll("");
        limpo = limpo.replace('\n', ' ').replace('\t', ' ');
        return ESPACOS.matcher(limpo).replaceAll(" ").trim();
    }

    /** Texto de varias linhas (descricao): sem tags; preserva paragrafos. */
    public static String texto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replace("\r\n", "\n").replace('\r', '\n');
        limpo = semHtml(limpo);
        limpo = CONTROLE.matcher(limpo).replaceAll("");
        limpo = ESPACOS.matcher(limpo).replaceAll(" ");
        limpo = LINHAS_EM_EXCESSO.matcher(limpo).replaceAll("\n\n");
        return limpo.trim();
    }

    private static String semHtml(String texto) {
        String limpo = Encoding.decodeHtml(NENHUM_HTML.sanitize(texto));
        // Duas passadas: decodificar pode revelar marcacao escondida em entidades (&lt;script&gt;)
        limpo = TAG.matcher(limpo).replaceAll("");
        return TAG.matcher(limpo).replaceAll("");
    }
}
