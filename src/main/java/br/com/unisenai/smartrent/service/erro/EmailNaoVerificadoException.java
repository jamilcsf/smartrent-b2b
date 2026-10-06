package br.com.unisenai.smartrent.service.erro;

/** O e-mail da conta ainda nao foi verificado e a acao exige isso (HTTP 403, como qualquer acesso negado). */
public class EmailNaoVerificadoException extends AcessoNegadoException {

    public static final String MENSAGEM = "Confirme o seu e-mail para enviar mensagens ou iniciar conversas. "
            + "Você ainda pode ler as suas conversas.";

    public EmailNaoVerificadoException() {
        super(MENSAGEM);
    }
}
