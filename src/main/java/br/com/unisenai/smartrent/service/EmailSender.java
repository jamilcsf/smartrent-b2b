package br.com.unisenai.smartrent.service;

/**
 * Envio de e-mail para um ENDERECO (nao para um usuario cadastrado): serve a avisos de
 * seguranca, ao link de confirmacao enviado ao novo e-mail e a notificacao da equipe.
 * A implementacao padrao so registra em log ({@link EmailSenderLog}); para enviar de verdade
 * (SMTP, SES etc.), registre outro bean marcado como {@code @Primary}, configurado por
 * variaveis de ambiente. Falha de envio nunca deve desfazer a acao que a originou.
 */
public interface EmailSender {

    /** Nome do canal, para logs e auditoria nao fingirem que um e-mail saiu quando foi so log. */
    String canal();

    void enviar(String para, String assunto, String corpo);
}
