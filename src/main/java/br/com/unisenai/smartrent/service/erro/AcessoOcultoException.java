package br.com.unisenai.smartrent.service.erro;

/**
 * O recurso existe, mas pertence a outra pessoa: o servico recusa (e continua sendo um {@link AcessoNegadoException}
 * para quem trata o erro), porem a API responde 404 com a MESMA mensagem de "nao encontrado". Assim 403 e 404 nao
 * revelam se um id de reserva, notificacao ou imovel existe.
 */
public class AcessoOcultoException extends AcessoNegadoException {
    public AcessoOcultoException(String mensagemDeNaoEncontrado) {
        super(mensagemDeNaoEncontrado);
    }
}
