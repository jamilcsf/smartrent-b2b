package br.com.unisenai.smartrent.service.erro;

/** Operacao incompativel com o estado atual do anuncio (HTTP 409). */
public class TransicaoInvalidaException extends RuntimeException {
    public TransicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
