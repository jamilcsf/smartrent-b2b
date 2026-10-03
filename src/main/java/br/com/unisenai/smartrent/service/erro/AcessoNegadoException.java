package br.com.unisenai.smartrent.service.erro;

/** Usuario autenticado, mas sem direito sobre o recurso (HTTP 403). */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
