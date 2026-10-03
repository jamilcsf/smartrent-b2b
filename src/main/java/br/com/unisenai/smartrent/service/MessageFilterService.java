package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro de conteudo do SmartChat: telefones, e-mails, links externos,
 * ofensas e termos sexuais explicitos. A mensagem NAO e recusada: os trechos
 * detectados sao trocados, <b>no servidor, antes de qualquer envio</b>, por um
 * marcador com texto de preenchimento. O original nunca sai deste servico para o
 * navegador (nem por CSS escondido): o front apenas desenha o borrao sobre o
 * preenchimento, que nao tem relacao com o conteudo real.
 *
 * <p>Formato do marcador: {@code U+E000 + codigo + U+E001 + preenchimento + U+E002}.
 * Os tres caracteres sao removidos do que o usuario digita, para ninguem forjar um marcador.
 *
 * <p>Falsos positivos: datas, horas, valores em R$, quantidades ("4 pessoas"),
 * codigos da plataforma (IMV-000123, #123) e CEP sao mascarados antes da busca
 * por telefone. Regras e listas sao configuraveis ({@link ChatProperties}) e
 * ficam isoladas aqui, para ajuste sem tocar o fluxo do chat.
 */
@Service
public class MessageFilterService implements ContentModerationService {

    public enum Categoria {
        TELEFONE('T', "Telefone ocultado"),
        EMAIL('E', "E-mail ocultado"),
        LINK('L', "Link externo ocultado"),
        SEXUAL('S', "Conteúdo impróprio ocultado"),
        OFENSA('O', "Conteúdo ofensivo ocultado");

        public final char codigo;
        public final String rotulo;

        Categoria(char codigo, String rotulo) {
            this.codigo = codigo;
            this.rotulo = rotulo;
        }
    }

    public static final char ABRE = '';
    public static final char MEIO = '';
    public static final char FECHA = '';

    /** Resultado: texto ja com marcadores (unico que sai do servidor), categorias e numero de trechos. */
    public record Resultado(String texto, Set<Categoria> categorias, int ocorrencias) {
        public boolean alterado() {
            return ocorrencias > 0;
        }
    }

    private record Trecho(int ini, int fim, Categoria categoria) {
    }

    // ------------------------------------------------------------- padroes

    private static final String TLDS = "com|net|org|br|io|me|co|app|dev|link|ly|gl|be|to|info|biz|xyz|site|online|store|shop|tv|gg|chat|us|uk|pt|ar|edu|gov|ai|cc|ws|im|ee|page|click|top|club";

    private static final Pattern URL_ESQUEMA = Pattern.compile("(?i)\\b(?:https?|ftp)://[^\\s<>\"']+");
    private static final Pattern URL_WWW = Pattern.compile("(?i)\\bwww\\.[^\\s<>\"']+");
    private static final Pattern DOMINIO_NU = Pattern.compile(
            "(?i)(?<![@\\w.\\-/])(?:[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?\\.)+(?:" + TLDS + ")(?![a-z0-9\\-])(?:[/?#][^\\s<>\"']*)?");
    private static final Pattern DOMINIO_FALADO = Pattern.compile(
            "(?i)\\b([a-z0-9][a-z0-9\\-]{1,})\\s*(?:[\\[(]?\\s*(?:ponto|dot)\\s*[\\])]?|[\\[(]\\s*\\.\\s*[\\])])\\s*(?:com|net|org|br|io|me|co|app|dev|link)\\b"
                    + "(?:\\s*(?:[\\[(]?\\s*(?:ponto|dot)\\s*[\\])]?|\\.)\\s*br\\b)?");
    private static final Set<String> PALAVRAS_COMUNS = Set.of("no", "na", "o", "a", "um", "uma", "esse", "essa", "nesse", "nessa",
            "neste", "nesta", "este", "esta", "desse", "dessa", "deste", "desta", "aquele", "aquela", "do", "da", "de", "em", "com", "por",
            "ao", "pelo", "pela", "outro", "outra", "cada", "ponto", "dot", "e", "ou", "que", "seu", "sua", "meu", "minha");
    private static final Pattern ARROBA_USUARIO = Pattern.compile("(?<![\\w.@])@[A-Za-z0-9._]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+\\-]+\\s*@\\s*[a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)+");
    private static final Pattern EMAIL_FALADO = Pattern.compile("(?i)\\b[a-z0-9._\\-]+\\s*(?:\\(?arroba\\)?|\\[at\\]|\\(at\\))\\s*[a-z0-9\\-]+");

    private static final Pattern FONE_COM_DDD = Pattern.compile(
            "(?<!\\d)(?:\\+?\\s*55[\\s.\\-]*)?\\(?\\s*0?[1-9]\\d\\s*\\)?[\\s.\\-]*(?:9[\\s.\\-]*)?\\d{4}[\\s.\\-]*\\d{4}(?!\\d)");
    private static final Pattern FONE_CELULAR = Pattern.compile("(?<!\\d)9[\\s.\\-]?\\d{4}[\\s.\\-]?\\d{4}(?!\\d)");
    private static final Pattern FONE_SEPARADO = Pattern.compile("(?<!\\d)(\\d{4})[\\s.\\-](\\d{4})(?!\\d)");
    private static final Pattern FONE_DISFARCADO = Pattern.compile("(?<!\\d)(?:\\d[\\s.\\-_*|]{1,3}){9,12}\\d(?!\\d)");
    private static final Pattern FONE_POR_EXTENSO = Pattern.compile(
            "(?<![a-z])(?:(?:zero|um|uma|dois|duas|tres|quatro|cinco|seis|meia|sete|oito|nove)(?![a-z])[\\s,.\\-]*){8,}");
    private static final Pattern FONE_LEET = Pattern.compile("(?<![\\p{L}\\d])[0-9OoIlSsBb][0-9OoIlSsBb\\s.\\-]{9,}[0-9OoIlSsBb](?![\\p{L}\\d])");

    /** Dados legitimos da reserva que nao podem ser tomados por telefone: mascarados antes da busca. */
    private static final List<Pattern> PROTEGIDOS = List.of(
            Pattern.compile("\\b\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}\\b"),
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
            Pattern.compile("\\b\\d{1,2}(?::|h)\\d{2}\\b"),
            Pattern.compile("(?i)R\\$\\s*\\d[\\d.,]*"),
            Pattern.compile("\\b\\d[\\d.]*,\\d{2}\\b"),
            Pattern.compile("\\b\\d{5}-\\d{3}\\b"),
            Pattern.compile("(?i)\\b(?:IMV|RES|RSV|RESERVA)[\\s\\-#]?\\d+\\b"),
            Pattern.compile("#\\d+"),
            Pattern.compile("(?i)\\b\\d+\\s*(?:x\\s*)?(?:pessoas?|h[oó]spedes?|di[aá]rias?|noites?|quartos?|banheiros?|vagas?|camas?|dias?|horas?|m2|m²|reais|real|anos?|crian[cç]as?)\\b"));

    private final ChatProperties props;
    private final Pattern ofensas;
    private final Pattern sexuais;

    public MessageFilterService(ChatProperties props) {
        this.props = props;
        Map<Categoria, List<String>> termos = carregarTermos(props);
        this.ofensas = compilarTermos(termos.get(Categoria.OFENSA));
        this.sexuais = compilarTermos(termos.get(Categoria.SEXUAL));
    }

    // ------------------------------------------------------------- API

    @Override
    public Resultado moderarTexto(String texto) {
        return filtrar(texto);
    }

    /** Aplica todas as regras e devolve o texto com os trechos substituidos por marcadores. */
    public Resultado filtrar(String entrada) {
        if (entrada == null || entrada.isEmpty()) {
            return new Resultado("", EnumSet.noneOf(Categoria.class), 0);
        }
        String texto = limparMarcadores(entrada);
        List<Trecho> achados = new ArrayList<>();
        detectarTelefones(texto, achados);
        detectarEmails(texto, achados);
        detectarLinks(texto, achados);
        detectarTermos(texto, achados);
        return montar(texto, achados);
    }

    /** Remove do que o usuario digitou os caracteres reservados do marcador (ninguem forja um borrao ou o desfaz). */
    static String limparMarcadores(String s) {
        return s.replace(String.valueOf(ABRE), "").replace(String.valueOf(MEIO), "").replace(String.valueOf(FECHA), "");
    }

    // ---------------------------------------------------------- telefones

    private static String mascarar(String texto) {
        char[] c = texto.toCharArray();
        for (Pattern p : PROTEGIDOS) {
            Matcher m = p.matcher(texto);
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) {
                    c[i] = 'A'; // letra neutra: some para a busca de numeros, mantem os indices
                }
            }
        }
        return new String(c);
    }

    private void detectarTelefones(String texto, List<Trecho> out) {
        String m = mascarar(texto);
        adicionar(FONE_COM_DDD.matcher(m), Categoria.TELEFONE, out);
        adicionar(FONE_CELULAR.matcher(m), Categoria.TELEFONE, out);
        Matcher sep = FONE_SEPARADO.matcher(m);
        while (sep.find()) {
            if (!ehAno(sep.group(1)) || !ehAno(sep.group(2))) { // "2026 2027" e um intervalo de anos
                out.add(new Trecho(sep.start(), sep.end(), Categoria.TELEFONE));
            }
        }
        Matcher dis = FONE_DISFARCADO.matcher(m);
        while (dis.find()) {
            if (contarDigitos(dis.group()) >= 10) {
                out.add(new Trecho(dis.start(), dis.end(), Categoria.TELEFONE));
            }
        }
        // Numeros por extenso ("nove nove nove ...") e com letras no lugar de digitos ("4l 9999-OOOO").
        String semAcento = semAcentoMesmoTamanho(m);
        adicionar(FONE_POR_EXTENSO.matcher(semAcento), Categoria.TELEFONE, out);
        Matcher leet = FONE_LEET.matcher(m);
        while (leet.find()) {
            String g = leet.group();
            long digitos = g.chars().filter(Character::isDigit).count();
            long letras = g.chars().filter(ch -> "OoIlSsBb".indexOf(ch) >= 0).count();
            if (digitos >= 5 && letras >= 1 && digitos + letras >= 10) {
                out.add(new Trecho(leet.start(), leet.end(), Categoria.TELEFONE));
            }
        }
    }

    private static boolean ehAno(String s) {
        int v = Integer.parseInt(s);
        return v >= 1900 && v <= 2100;
    }

    private static int contarDigitos(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------- e-mail e links

    private void detectarEmails(String texto, List<Trecho> out) {
        adicionar(EMAIL.matcher(texto), Categoria.EMAIL, out);
        adicionar(EMAIL_FALADO.matcher(texto), Categoria.EMAIL, out);
    }

    private void detectarLinks(String texto, List<Trecho> out) {
        Matcher m = URL_ESQUEMA.matcher(texto);
        while (m.find()) {
            adicionarLink(texto, m.start(), m.end(), out);
        }
        m = URL_WWW.matcher(texto);
        while (m.find()) {
            adicionarLink(texto, m.start(), m.end(), out);
        }
        m = DOMINIO_NU.matcher(texto);
        while (m.find()) {
            adicionarLink(texto, m.start(), m.end(), out);
        }
        m = DOMINIO_FALADO.matcher(texto);
        while (m.find()) {
            if (!PALAVRAS_COMUNS.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                out.add(new Trecho(m.start(), m.end(), Categoria.LINK));
            }
        }
        adicionar(ARROBA_USUARIO.matcher(texto), Categoria.LINK, out);
    }

    private void adicionarLink(String texto, int ini, int fim, List<Trecho> out) {
        // pontuacao final da frase nao faz parte do link
        while (fim > ini && ".,;:!?)".indexOf(texto.charAt(fim - 1)) >= 0) {
            fim--;
        }
        if (fim <= ini) {
            return;
        }
        if (dominioPermitido(texto.substring(ini, fim))) {
            return;
        }
        out.add(new Trecho(ini, fim, Categoria.LINK));
    }

    /** Dominio da plataforma (e subdominios) passa; qualquer outro e externo. */
    boolean dominioPermitido(String link) {
        String host = link.replaceFirst("(?i)^[a-z]+://", "");
        host = host.replaceFirst("^[^/@]*@", "");
        host = host.split("[/?#]", 2)[0].replaceFirst(":\\d+$", "").toLowerCase(Locale.ROOT);
        for (String permitido : props.dominiosPermitidos()) {
            String d = permitido.trim().toLowerCase(Locale.ROOT);
            if (!d.isEmpty() && (host.equals(d) || host.endsWith("." + d))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------- ofensas e sexuais

    private void detectarTermos(String texto, List<Trecho> out) {
        Normalizado n = normalizar(texto);
        coletarTermos(n, ofensas, Categoria.OFENSA, out);
        coletarTermos(n, sexuais, Categoria.SEXUAL, out);
    }

    private static void coletarTermos(Normalizado n, Pattern p, Categoria cat, List<Trecho> out) {
        if (p == null) {
            return;
        }
        Matcher m = p.matcher(n.texto);
        while (m.find()) {
            int ini = n.indice[m.start()];
            int fim = n.indice[m.end() - 1] + 1;
            out.add(new Trecho(ini, fim, cat));
        }
    }

    /** Texto em minusculas, sem acento, com leet desfeito, so letras e espacos; guarda o indice original de cada caractere. */
    private record Normalizado(String texto, int[] indice) {
    }

    private static Normalizado normalizar(String original) {
        StringBuilder sb = new StringBuilder();
        List<Integer> idx = new ArrayList<>();
        boolean espaco = false;
        for (int i = 0; i < original.length(); i++) {
            char c = Character.toLowerCase(original.charAt(i));
            String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char base = d.isEmpty() ? c : d.charAt(0);
            char mapa = switch (base) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                case '@' -> 'a';
                case '$' -> 's';
                default -> base;
            };
            if (mapa >= 'a' && mapa <= 'z') {
                sb.append(mapa);
                idx.add(i);
                espaco = false;
            } else if (!espaco) { // qualquer outro simbolo vira um unico espaco
                sb.append(' ');
                idx.add(i);
                espaco = true;
            }
        }
        int[] indice = idx.stream().mapToInt(Integer::intValue).toArray();
        return new Normalizado(sb.toString(), indice);
    }

    /** Minusculas e sem acento, caractere a caractere: o tamanho nao muda, entao os indices continuam valendo. */
    private static String semAcentoMesmoTamanho(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            String d = Normalizer.normalize(String.valueOf(Character.toLowerCase(s.charAt(i))), Normalizer.Form.NFD);
            sb.append(d.isEmpty() ? s.charAt(i) : d.charAt(0));
        }
        return sb.toString();
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Um termo vira regex tolerante: letras repetidas e, em termos de 4+ letras, separadores entre as letras. */
    private static Pattern compilarTermos(List<String> termos) {
        if (termos == null || termos.isEmpty()) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        for (String termo : termos) {
            String t = semAcento(termo).toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "");
            if (t.isBlank()) {
                continue;
            }
            boolean separadores = t.replace(" ", "").length() >= 4;
            StringBuilder re = new StringBuilder();
            for (char c : t.toCharArray()) {
                if (c == ' ') {
                    re.append("\\s+");
                } else {
                    re.append(c).append('+');
                    re.append(separadores ? "\\s?" : "");
                }
            }
            partes.add(re.toString().replaceAll("(\\\\s\\?)$", ""));
        }
        // mais longos primeiro: "filho da puta" vence "puta"
        partes.sort(Comparator.comparingInt(String::length).reversed());
        return Pattern.compile("(?<![a-z])(?:" + String.join("|", partes) + ")s?(?![a-z])");
    }

    private static Map<Categoria, List<String>> carregarTermos(ChatProperties props) {
        Map<Categoria, List<String>> mapa = new LinkedHashMap<>();
        mapa.put(Categoria.OFENSA, new ArrayList<>());
        mapa.put(Categoria.SEXUAL, new ArrayList<>());
        try (InputStream in = abrirArquivoDeTermos(props)) {
            if (in == null) {
                return mapa;
            }
            for (String linha : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String l = linha.trim();
                if (l.isEmpty() || l.startsWith("#") || !l.contains("|")) {
                    continue;
                }
                String[] p = l.split("\\|", 2);
                try {
                    mapa.get(Categoria.valueOf(p[0].trim().toUpperCase(Locale.ROOT))).add(p[1].trim());
                } catch (IllegalArgumentException | NullPointerException e) {
                    // categoria desconhecida: linha ignorada
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler a lista de termos do SmartChat.", e);
        }
        return mapa;
    }

    private static InputStream abrirArquivoDeTermos(ChatProperties props) throws IOException {
        if (props.termosArquivo() != null && !props.termosArquivo().isBlank()) {
            return Files.newInputStream(Path.of(props.termosArquivo()));
        }
        return MessageFilterService.class.getResourceAsStream("/smartchat-termos.txt");
    }

    // ------------------------------------------------------------ montagem

    private static void adicionar(Matcher m, Categoria cat, List<Trecho> out) {
        while (m.find()) {
            out.add(new Trecho(m.start(), m.end(), cat));
        }
    }

    /** Une trechos que se sobrepoem ou encostam e troca cada um por um marcador de preenchimento. */
    private static Resultado montar(String texto, List<Trecho> achados) {
        if (achados.isEmpty()) {
            return new Resultado(texto, EnumSet.noneOf(Categoria.class), 0);
        }
        // espaco na ponta nao faz parte do trecho borrado
        List<Trecho> aparados = new ArrayList<>();
        for (Trecho t : achados) {
            int ini = t.ini(), fim = t.fim();
            while (ini < fim && Character.isWhitespace(texto.charAt(ini))) {
                ini++;
            }
            while (fim > ini && Character.isWhitespace(texto.charAt(fim - 1))) {
                fim--;
            }
            if (fim > ini) {
                aparados.add(new Trecho(ini, fim, t.categoria()));
            }
        }
        achados = aparados;
        if (achados.isEmpty()) {
            return new Resultado(texto, EnumSet.noneOf(Categoria.class), 0);
        }
        achados.sort(Comparator.comparingInt(Trecho::ini).thenComparingInt(t -> -t.fim()));
        List<int[]> unidos = new ArrayList<>();
        List<Categoria> cats = new ArrayList<>();
        Set<Categoria> todas = EnumSet.noneOf(Categoria.class);
        for (Trecho t : achados) {
            todas.add(t.categoria());
            if (!unidos.isEmpty() && t.ini() <= unidos.get(unidos.size() - 1)[1]) {
                int[] u = unidos.get(unidos.size() - 1);
                u[1] = Math.max(u[1], t.fim());
            } else {
                unidos.add(new int[]{t.ini(), t.fim()});
                cats.add(t.categoria());
            }
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < unidos.size(); i++) {
            int[] u = unidos.get(i);
            sb.append(texto, pos, u[0]);
            sb.append(ABRE).append(cats.get(i).codigo).append(MEIO).append(preenchimento(u[1] - u[0])).append(FECHA);
            pos = u[1];
        }
        sb.append(texto.substring(pos));
        return new Resultado(sb.toString(), todas, unidos.size());
    }

    /** Letras aleatorias, sem relacao com o conteudo real; o tamanho so acompanha o trecho de forma aproximada. */
    private static String preenchimento(int tamanho) {
        String letras = "abcdefghnoprstuvxz";
        int n = Math.max(6, Math.min(18, tamanho));
        StringBuilder sb = new StringBuilder(n);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            sb.append(letras.charAt(r.nextInt(letras.length())));
        }
        return sb.toString();
    }

    /** Texto "puro" para notificacoes e pre-visualizacoes: marcadores viram a etiqueta da categoria. */
    public static String semMarcadores(String comMarcadores) {
        if (comMarcadores == null) {
            return null;
        }
        Matcher m = Pattern.compile(ABRE + "(.)" + MEIO + "[^" + FECHA + "]*" + FECHA).matcher(comMarcadores);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("[ocultado]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
