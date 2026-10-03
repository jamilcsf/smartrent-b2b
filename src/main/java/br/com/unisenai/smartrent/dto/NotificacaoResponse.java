package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Notificacao;

import java.time.LocalDateTime;

/** Notificacao in-app como a API a expoe. */
public record NotificacaoResponse(
        Long id,
        Long imovelId,
        String titulo,
        String mensagem,
        String link,
        boolean lida,
        LocalDateTime criadaEm) {

    public static NotificacaoResponse de(Notificacao n) {
        return new NotificacaoResponse(n.getId(), n.getImovelId(), n.getTitulo(), n.getMensagem(),
                n.getLink(), n.isLida(), n.getCriadaEm());
    }
}
