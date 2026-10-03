package br.com.unisenai.smartrent.service;

import java.util.regex.Pattern;

/**
 * Limpeza de texto livre digitado pelo gestor (titulo, descricao...).
 *
 * <p>Remove marcacao HTML e caracteres de controle. E defesa em profundidade:
 * o front tambem escapa tudo o que renderiza, mas o dado nao deve chegar ao
 * banco carregando {@code <script>} para o dia em que alguem o consumir sem
 * escapar.
 */
public final class Sanitizador {

    private static final Pattern TAG = Pattern.compile("<[^>]*>?");
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
        String limpo = TAG.matcher(texto).replaceAll("");
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
        limpo = TAG.matcher(limpo).replaceAll("");
        limpo = CONTROLE.matcher(limpo).replaceAll("");
        limpo = ESPACOS.matcher(limpo).replaceAll(" ");
        limpo = LINHAS_EM_EXCESSO.matcher(limpo).replaceAll("\n\n");
        return limpo.trim();
    }
}
