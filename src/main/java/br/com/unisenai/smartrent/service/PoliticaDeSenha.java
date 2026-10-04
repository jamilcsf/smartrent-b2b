package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Politica da NOVA senha: tamanho minimo (padrao 10), limite do BCrypt (72 bytes), nao ser
 * senha comum/vazada (lista local, ver {@code senhas-comuns.txt}), nao ser repetitiva e nao
 * conter o e-mail da conta. A regra "diferente da atual" fica no servico que tem o hash.
 * Nada aqui envia a senha para fora nem a registra.
 */
@Component
public class PoliticaDeSenha {

    /** O BCrypt so usa os primeiros 72 bytes: acima disso recusamos em vez de truncar em silencio. */
    static final int MAX_BYTES = 72;

    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern NAO_ALFANUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern DIGITOS_FINAIS = Pattern.compile("[0-9]+$");

    private final int minimo;
    private final Set<String> comuns = new HashSet<>();

    public PoliticaDeSenha(PerfilProperties props) {
        this.minimo = props.senhaMinima();
        carregar(props.senhasComunsArquivo());
    }

    /** Lanca {@link IllegalArgumentException} com a mensagem para o usuario. */
    public void validar(String nova, String email) {
        if (nova == null || nova.codePointCount(0, nova.length()) < minimo) {
            throw new IllegalArgumentException("A nova senha deve ter ao menos " + minimo + " caracteres.");
        }
        if (nova.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("A nova senha é longa demais (máximo de " + MAX_BYTES + " bytes).");
        }
        String base = base(nova);
        if (ehComum(base) || ehComum(base(nova.replace('@', ' ').replace('$', ' ')))) {
            throw new IllegalArgumentException("Essa senha é muito comum. Escolha outra, mais difícil de adivinhar.");
        }
        if (base.chars().distinct().count() <= 3) {
            throw new IllegalArgumentException("A senha é repetitiva demais. Misture mais caracteres.");
        }
        if (email != null && !email.isBlank()) {
            String local = base(email.substring(0, Math.max(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'))));
            if (local.length() >= 4 && base.contains(local)) {
                throw new IllegalArgumentException("A senha não pode conter o seu e-mail.");
            }
        }
    }

    /** Vale a variante inteira ou sem os numeros do fim (senha2025 e senha). */
    private boolean ehComum(String base) {
        return comuns.contains(leet(base)) || comuns.contains(leet(DIGITOS_FINAIS.matcher(base).replaceAll("")));
    }

    /** Minusculas, sem acentos e so letras/numeros. */
    static String base(String s) {
        String semAcento = ACENTOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        // "@" e "$" no meio de palavra sao letras disfarcadas (p@ssword, $enha); os demais simbolos somem.
        String minusculas = semAcento.toLowerCase(java.util.Locale.ROOT).replace('@', 'a').replace('$', 's');
        return NAO_ALFANUM.matcher(minusculas).replaceAll("");
    }

    /** Desfaz as trocas comuns de letra por numero (4=a, 0=o, 3=e, 1=i, 5=s, 7=t). */
    static String leet(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return sb.toString();
    }

    private void carregar(String arquivoExterno) {
        try (InputStream in = abrir(arquivoExterno);
             BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String t = linha.trim();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    comuns.add(leet(base(t)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel carregar a lista de senhas comuns.", e);
        }
    }

    private static InputStream abrir(String arquivoExterno) throws IOException {
        if (arquivoExterno != null && !arquivoExterno.isBlank()) {
            return Files.newInputStream(Path.of(arquivoExterno));
        }
        InputStream in = PoliticaDeSenha.class.getResourceAsStream("/senhas-comuns.txt");
        if (in == null) {
            throw new IOException("senhas-comuns.txt ausente do classpath");
        }
        return in;
    }
}
