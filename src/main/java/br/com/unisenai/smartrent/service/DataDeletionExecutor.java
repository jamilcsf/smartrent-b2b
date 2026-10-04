package br.com.unisenai.smartrent.service;

/**
 * Ponto de extensao da EXECUCAO da exclusao/anonimizacao. Fora do escopo desta etapa e SEM
 * implementacao de proposito: o que apagar, o que reter por obrigacao legal e como tratar
 * reservas, conversas e logs depende de definicao do setor juridico. Enquanto nao houver um
 * bean desta interface, {@link DataDeletionReviewService#concluir} recusa a conclusao e
 * nenhum dado e tocado.
 */
public interface DataDeletionExecutor {

    /** Executa a exclusao (ou anonimizacao) dos dados do usuario. Deve ser idempotente. */
    void executar(Long usuarioId);
}
