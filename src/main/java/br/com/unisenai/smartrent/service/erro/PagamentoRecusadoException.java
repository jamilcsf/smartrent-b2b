package br.com.unisenai.smartrent.service.erro;

/** O gateway recusou a cobranca (HTTP 402). A tentativa fica registrada; a reserva segue pendente. */
public class PagamentoRecusadoException extends RuntimeException {
    public PagamentoRecusadoException(String mensagem) {
        super(mensagem);
    }
}
