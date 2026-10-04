package br.com.unisenai.smartrent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de uso unico (confirmacao de e-mail, "nao fui eu" da exclusao de dados): 256 bits
 * aleatorios, entregues ao usuario UMA vez (no link). O banco guarda so o SHA-256 em hex:
 * quem le o banco nao consegue usar o link. SHA-256 simples basta porque o token ja tem
 * entropia total (nao e senha escolhida por pessoa).
 */
public final class TokenSeguro {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenSeguro() {
    }

    /** Token novo, seguro para URL (43 caracteres). */
    public static String gerar() {
        byte[] b = new byte[32];
        ALEATORIO.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** SHA-256 em hexadecimal (64 caracteres). */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
