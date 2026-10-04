package br.com.unisenai.smartrent.service.erro;

import java.util.Map;

/** Dados do anuncio invalidos; carrega as mensagens por campo (HTTP 400). */
public class ValidacaoAnuncioException extends RuntimeException {

    private final Map<String, String> campos;

    public ValidacaoAnuncioException(String mensagem, Map<String, String> campos) {
        super(mensagem);
        this.campos = campos;
    }

    public ValidacaoAnuncioException(String mensagem) {
        this(mensagem, Map.of());
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
