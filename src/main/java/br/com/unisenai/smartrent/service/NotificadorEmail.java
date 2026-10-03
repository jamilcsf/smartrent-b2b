package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Usuario;

/**
 * Ponto de extensao para o canal de e-mail. O projeto ainda nao tem SMTP
 * configurado; enquanto isso a implementacao padrao so registra em log. Para
 * enviar de verdade, basta um bean que implemente esta interface.
 */
public interface NotificadorEmail {

    /** Nome do canal gravado no registro de lembretes. */
    String canal();

    /** Envia a mensagem; qualquer excecao e tratada pelo chamador como falha de entrega. */
    void enviar(Usuario destino, String assunto, String corpo);
}
