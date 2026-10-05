package br.com.unisenai.smartrent.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Cifra de campo em repouso: AES-256-GCM, IV aleatorio de 12 bytes por valor e tag de 128 bits.
 *
 * <p>Formato gravado: {@code "v1:" + Base64(IV || ciphertext+tag)}. O prefixo e a versao da chave e
 * permite rotacao futura. Valor sem prefixo de versao e tratado como texto puro legado (migracao).
 * Valor com prefixo e tag invalida lanca {@link FalhaDecifragemException}; nunca devolve lixo.
 *
 * <p>Isto NAO e criptografia ponta a ponta: o servidor tem a chave e le o texto.
 */
public final class CifraCampo {

    public static final String VERSAO_ATUAL = "v1";
    public static final int TAMANHO_CHAVE_BYTES = 32;

    private static final String PREFIXO_ATUAL = VERSAO_ATUAL + ":";
    private static final Pattern PREFIXO_DE_VERSAO = Pattern.compile("^v\\d+:");
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey chave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifraCampo(byte[] chave) {
        if (chave == null || chave.length != TAMANHO_CHAVE_BYTES) {
            throw new IllegalArgumentException("A chave de cifra deve ter exatamente 32 bytes (AES-256).");
        }
        this.chave = new SecretKeySpec(chave, "AES");
    }

    /** Interpreta a chave em Base64 (32 bytes). A mensagem de erro nunca contem o valor recebido. */
    public static CifraCampo deBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("A chave de cifra esta ausente.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("A chave de cifra nao esta em Base64 valido.");
        }
        if (bytes.length != TAMANHO_CHAVE_BYTES) {
            throw new IllegalArgumentException("A chave de cifra deve ter 32 bytes depois de decodificar o Base64 (tem "
                    + bytes.length + ").");
        }
        return new CifraCampo(bytes);
    }

    /** {@code null} entra, {@code null} sai. */
    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[IV_BYTES + cifrado.length];
            System.arraycopy(iv, 0, saida, 0, IV_BYTES);
            System.arraycopy(cifrado, 0, saida, IV_BYTES, cifrado.length);
            return PREFIXO_ATUAL + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o valor.", e);
        }
    }

    /**
     * {@code null} sai {@code null}; valor sem prefixo de versao volta como esta (legado em texto puro);
     * valor com prefixo e invalido lanca {@link FalhaDecifragemException}.
     */
    public String decifrar(String valor) {
        if (valor == null) {
            return null;
        }
        if (!estaCifrado(valor)) {
            return valor;
        }
        if (!valor.startsWith(PREFIXO_ATUAL)) {
            throw new FalhaDecifragemException("Versao de chave desconhecida no valor cifrado.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(valor.substring(PREFIXO_ATUAL.length()));
            if (bytes.length < IV_BYTES + TAG_BITS / 8) {
                throw new FalhaDecifragemException("Valor cifrado truncado.");
            }
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cifra.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FalhaDecifragemException("Nao foi possivel decifrar o valor (adulterado ou chave incorreta).", e);
        }
    }

    /**
     * Se o valor deve ser tratado como texto puro pela migracao: sem prefixo de versao, ou com prefixo mas que nao
     * decifra (ex.: legado "v1: teste"). So a migracao usa isto; a leitura continua lancando excecao para prefixo invalido.
     */
    public boolean ehTextoPuro(String valor) {
        if (valor == null) {
            return false;
        }
        if (!estaCifrado(valor)) {
            return true;
        }
        try {
            decifrar(valor);
            return false;
        } catch (FalhaDecifragemException e) {
            return true;
        }
    }

    /** Se o valor ja tem prefixo de versao ({@code vN:}); usado pela migracao para pular o que ja esta cifrado. */
    public static boolean estaCifrado(String valor) {
        return valor != null && PREFIXO_DE_VERSAO.matcher(valor).find();
    }
}
