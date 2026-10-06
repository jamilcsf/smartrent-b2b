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
        /** Mensageiros e redes sociais citados sozinhos ("me chama no zap"): o contato foi combinado fora da plataforma. */
        CONTATO_EXTERNO('C', "Contato externo ocultado"),
        SEXUAL('S', "Conteúdo impróprio ocultado"),
        OFENSA('O', "Conteúdo ofensivo ocultado"),
        /** Sinal, nunca mascara: pagamento ou contato fora da plataforma (o destinatario recebe um alerta). */
        SUSPEITA_FRAUDE('F', "Menção a pagamento ou contato fora da plataforma");

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
        /** Houve trecho mascarado. Suspeita de fraude NAO altera o texto: nao conta aqui. */
        public boolean alterado() {
            return ocorrencias > 0;
        }

        public boolean suspeitaFraude() {
            return categorias.contains(Categoria.SUSPEITA_FRAUDE);
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

    /** Mensageiros e redes sociais: mascarados mesmo isolados (sao o canal da disintermediacao). "pix" fica so como alerta. */
    private static final List<String> MENSAGEIROS = List.of("whatsapp", "whatsap", "whats", "wpp", "zap", "zapzap",
            "telegram", "instagram", "insta", "facebook", "messenger", "discord", "skype");

    /** Numero falado ("nove", "quatro"), com a mesma grafia sem acento usada em {@code semAcentoMesmoTamanho}. */
    private static final Pattern NUMERO_OU_PALAVRA = Pattern.compile(
            "(?<![a-z])(?:zero|um|uma|dois|duas|tres|quatro|cinco|seis|meia|sete|oito|nove)(?![a-z])|\\d+");

    private final ChatProperties props;
    private final Pattern ofensas;
    private final Pattern sexuais;
    private final Pattern fraude;
    private final Pattern mensageiros;

    public MessageFilterService(ChatProperties props) {
        this.props = props;
        Map<Categoria, List<String>> termos = carregarTermos(props);
        this.ofensas = compilarTermos(termos.get(Categoria.OFENSA));
        this.sexuais = compilarTermos(termos.get(Categoria.SEXUAL));
        this.fraude = compilarTermos(termos.get(Categoria.SUSPEITA_FRAUDE));
        this.mensageiros = compilarTermos(MENSAGEIROS);
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
        String texto = normalizarUnicode(limparMarcadores(entrada));
        List<Trecho> achados = new ArrayList<>();
        detectarTelefones(texto, achados);
        detectarEmails(texto, achados);
        detectarLinks(texto, achados);
        detectarNoTextoColapsado(texto, achados);
        detectarTermos(texto, achados);
        Resultado r = montar(texto, achados);
        if (mencionaPagamentoOuContatoPorFora(texto)) {
            // So registra a categoria: o texto segue como foi digitado e as ocorrencias nao mudam.
            Set<Categoria> categorias = EnumSet.noneOf(Categoria.class);
            categorias.addAll(r.categorias());
            categorias.add(Categoria.SUSPEITA_FRAUDE);
            return new Resultado(r.texto(), categorias, r.ocorrencias());
        }
        return r;
    }

    /** Termos de golpe/pagamento por fora (lista configuravel), com a mesma tolerancia dos termos ofensivos. */
    private boolean mencionaPagamentoOuContatoPorFora(String texto) {
        return fraude != null && fraude.matcher(normalizar(texto).texto()).find();
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
        detectarTelefonesMistos(semAcento, out);
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

    /**
     * Antes de qualquer regex: remove caracteres invisiveis (zero-width, soft hyphen, seletores de variacao, o
     * quadrinho dos digitos-emoji), aplica NFKC (digitos e pontos fullwidth, espacos especiais, digitos matematicos),
     * converte qualquer digito nao ASCII (arabe-indico etc.) e o ponto ideografico. Sem isso, "４８ 9999-0000" ou
     * "48 99999&#8203;0000" passavam pelo filtro. O texto devolvido ao usuario sai da versao normalizada.
     */
    static String normalizarUnicode(String s) {
        StringBuilder limpo = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.getType(cp) == Character.FORMAT || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF)
                    || cp == 0x20E3 || cp == 0x034F || cp == 0x3164 || cp == 0x2800) {
                continue;
            }
            limpo.appendCodePoint(cp);
        }
        String nfkc = Normalizer.normalize(limpo, Normalizer.Form.NFKC);
        StringBuilder saida = new StringBuilder(nfkc.length());
        for (int i = 0; i < nfkc.length(); ) {
            int cp = nfkc.codePointAt(i);
            i += Character.charCount(cp);
            if (cp > 127 && Character.isDigit(cp)) {
                saida.append((char) ('0' + Character.digit(cp, 10)));
            } else if (cp == 0x3002) {
                saida.append('.');
            } else {
                saida.appendCodePoint(cp);
            }
        }
        return saida.toString();
    }

    /** Texto sem o espaco entre tokens de UM caractere ("j o a o @ g m a i l . c o m"), com o indice original. */
    private record Colapsado(String texto, int[] indice) {
    }

    private static Colapsado colapsarEspacos(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        List<Integer> idx = new ArrayList<>();
        int n = texto.length();
        for (int i = 0; i < n; i++) {
            char c = texto.charAt(i);
            if (c == ' ' && i > 0 && i < n - 1 && tokenUnico(texto, i - 1, -1) && tokenUnico(texto, i + 1, 1)) {
                continue;
            }
            sb.append(c);
            idx.add(i);
        }
        return new Colapsado(sb.toString(), idx.stream().mapToInt(Integer::intValue).toArray());
    }

    /** O caractere em {@code pos} e um token sozinho (vizinho do outro lado e espaco ou borda)? */
    private static boolean tokenUnico(String t, int pos, int direcao) {
        char c = t.charAt(pos);
        if (!(Character.isLetterOrDigit(c) || c == '@' || c == '.')) {
            return false;
        }
        int alem = pos + direcao;
        return alem < 0 || alem >= t.length() || t.charAt(alem) == ' ';
    }

    /** E-mail e link escritos com espacos entre as letras ("g m a i l ponto com"): procura no texto colapsado. */
    private void detectarNoTextoColapsado(String texto, List<Trecho> out) {
        Colapsado c = colapsarEspacos(texto);
        if (c.texto().length() == texto.length()) {
            return;
        }
        coletarNoColapsado(c, EMAIL, Categoria.EMAIL, out, false);
        coletarNoColapsado(c, EMAIL_FALADO, Categoria.EMAIL, out, false);
        coletarNoColapsado(c, URL_WWW, Categoria.LINK, out, true);
        coletarNoColapsado(c, DOMINIO_NU, Categoria.LINK, out, true);
        Matcher m = DOMINIO_FALADO.matcher(c.texto());
        while (m.find()) {
            if (!PALAVRAS_COMUNS.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                out.add(new Trecho(c.indice()[m.start()], c.indice()[m.end() - 1] + 1, Categoria.LINK));
            }
        }
    }

    private void coletarNoColapsado(Colapsado c, Pattern p, Categoria cat, List<Trecho> out, boolean link) {
        Matcher m = p.matcher(c.texto());
        while (m.find()) {
            if (link && dominioPermitido(m.group())) {
                continue;
            }
            out.add(new Trecho(c.indice()[m.start()], c.indice()[m.end() - 1] + 1, cat));
        }
    }

    /**
     * Telefone misturando numeros por extenso e digitos ("cinco cinco quatro oito 9 9 9 9 9 0 0 0 0"): uma sequencia
     * so de digitos/palavras-numero, separada por espaco, virgula, ponto ou hifen, com 10 ou mais algarismos e ao
     * menos uma palavra. So digitos e so palavras ja sao tratados pelos outros padroes; datas, precos, CEP e
     * quantidades chegam aqui mascarados (letra neutra) e nunca formam a sequencia.
     */
    private static void detectarTelefonesMistos(String semAcento, List<Trecho> out) {
        Matcher m = NUMERO_OU_PALAVRA.matcher(semAcento);
        int ini = -1, fim = -1, algarismos = 0, palavras = 0;
        while (m.find()) {
            boolean continua = ini >= 0 && semAcento.substring(fim, m.start()).matches("[\\s,.\\-]*");
            if (!continua) {
                fecharMisto(ini, fim, algarismos, palavras, out);
                ini = m.start();
                algarismos = 0;
                palavras = 0;
            }
            fim = m.end();
            if (Character.isDigit(m.group().charAt(0))) {
                algarismos += m.group().length();
            } else {
                algarismos++;
                palavras++;
            }
        }
        fecharMisto(ini, fim, algarismos, palavras, out);
    }

    private static void fecharMisto(int ini, int fim, int algarismos, int palavras, List<Trecho> out) {
        if (ini >= 0 && algarismos >= 10 && palavras >= 1) {
            out.add(new Trecho(ini, fim, Categoria.TELEFONE));
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
        coletarTermos(n, mensageiros, Categoria.CONTATO_EXTERNO, out);
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
        mapa.put(Categoria.SUSPEITA_FRAUDE, new ArrayList<>());
        try (InputStream in = abrirArquivoDeTermos(props)) {
            lerTermos(in, mapa);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler a lista de termos do SmartChat.", e);
        }
        boolean arquivoProprio = props.termosArquivo() != null && !props.termosArquivo().isBlank();
        if (arquivoProprio && mapa.get(Categoria.SUSPEITA_FRAUDE).isEmpty()) {
            // Arquivo proprio sem linhas SUSPEITA_FRAUDE (anterior a esta categoria): mantem a lista padrao de fraude.
            try (InputStream padrao = MessageFilterService.class.getResourceAsStream("/smartchat-termos.txt")) {
                Map<Categoria, List<String>> defaults = new LinkedHashMap<>();
                defaults.put(Categoria.SUSPEITA_FRAUDE, mapa.get(Categoria.SUSPEITA_FRAUDE));
                lerTermos(padrao, defaults);
            } catch (IOException e) {
                throw new IllegalStateException("Nao foi possivel ler a lista padrao de termos do SmartChat.", e);
            }
        }
        return mapa;
    }

    private static void lerTermos(InputStream in, Map<Categoria, List<String>> mapa) throws IOException {
        if (in == null) {
            return;
        }
        for (String linha : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
            String l = linha.trim();
            if (l.isEmpty() || l.startsWith("#") || !l.contains("|")) {
                continue;
            }
            String[] p = l.split("\\|", 2);
            try {
                List<String> lista = mapa.get(Categoria.valueOf(p[0].trim().toUpperCase(Locale.ROOT)));
                if (lista != null) {
                    lista.add(p[1].trim());
                }
            } catch (IllegalArgumentException e) {
                // categoria desconhecida: linha ignorada
            }
        }
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
