package br.com.unisenai.smartrent.service.erro;

/** Recurso inexistente ou invisivel para quem pediu (HTTP 404). */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
