package br.com.unisenai.smartrent.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * HMAC-SHA-256 de um texto NORMALIZADO, para comparar mensagens iguais (envio em massa) sem guardar o texto.
 * Sem a chave, o resumo nao permite testar palpites de conteudo.
 */
public final class HmacTexto {

    public static final int TAMANHO_MINIMO_CHAVE_BYTES = 32;

    private final byte[] chave;

    public HmacTexto(byte[] chave) {
        if (chave == null || chave.length < TAMANHO_MINIMO_CHAVE_BYTES) {
            throw new IllegalArgumentException("A chave do HMAC deve ter pelo menos 32 bytes.");
        }
        this.chave = chave.clone();
    }

    /** Interpreta a chave em Base64 (pelo menos 32 bytes). A mensagem de erro nunca contem o valor recebido. */
    public static HmacTexto deBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("A chave do HMAC esta ausente.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("A chave do HMAC nao esta em Base64 valido.");
        }
        if (bytes.length < TAMANHO_MINIMO_CHAVE_BYTES) {
            throw new IllegalArgumentException("A chave do HMAC deve ter pelo menos 32 bytes depois de decodificar o Base64 (tem "
                    + bytes.length + ").");
        }
        return new HmacTexto(bytes);
    }

    /** Caixa, acento, espacos e pontuacao nao diferenciam dois textos: "Oi, tudo bem?" e " oi   TUDO bem " sao iguais. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder(semAcento.length());
        boolean espaco = true;
        for (int i = 0; i < semAcento.length(); i++) {
            char c = Character.toLowerCase(semAcento.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                espaco = false;
            } else if (!espaco) {
                sb.append(' ');
                espaco = true;
            }
        }
        return sb.toString().strip().toLowerCase(Locale.ROOT);
    }

    /** Hexadecimal do HMAC-SHA-256 do texto ja normalizado (64 caracteres). */
    public String resumo(String textoNormalizado) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(textoNormalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular o resumo da mensagem.", e);
        }
    }

    /** Atalho: normaliza e resume. */
    public String resumoDe(String texto) {
        return resumo(normalizar(texto));
    }
}
