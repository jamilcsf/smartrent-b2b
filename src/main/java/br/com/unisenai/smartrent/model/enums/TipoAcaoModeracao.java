package br.com.unisenai.smartrent.model.enums;

/** O que a administracao fez; cada valor e uma linha da trilha {@code moderacao_acoes}. */
public enum TipoAcaoModeracao {
    SUSPENSAO,
    REATIVACAO,
    MENSAGEM,
    DENUNCIA_PROCEDENTE,
    DENUNCIA_IMPROCEDENTE,
    /** Um admin abriu o detalhe de uma denuncia (a leitura das evidencias fica registrada). */
    DENUNCIA_ABERTA,
    ALERTA_REVISADO,
    ALERTA_DESCARTADO
}
