package br.com.unisenai.smartrent.service.erro;

/** Muitas tentativas em pouco tempo (HTTP 429). */
public class LimiteExcedidoException extends RuntimeException {
    public LimiteExcedidoException(String mensagem) {
        super(mensagem);
    }
}
