package br.com.unisenai.smartrent.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida do anuncio. As transicoes permitidas ficam aqui, num unico
 * lugar, e os servicos so as consultam: nenhum codigo troca o status sem
 * passar por {@link #podeIr(StatusAnuncio)}.
 */
public enum StatusAnuncio {
    /** Cadastrado, sem preco: ainda nao aparece no catalogo. */
    PRE_PUBLICACAO_SEM_PRECO,
    /** Preco confirmado pela primeira vez; a janela de 24h corre desde entao. */
    PRE_PUBLICACAO_AGUARDANDO,
    /** Janela cumprida: o gestor ja pode publicar. */
    PRONTO_PARA_PUBLICAR,
    /** No catalogo. */
    PUBLICADO,
    /** Fora do ar enquanto o gestor edita; sem prazo maximo. */
    EM_EDICAO,
    /** Edicao confirmada; volta sozinho ao catalogo em republicar_em. */
    REPUBLICACAO_AGENDADA;

    public boolean podeIr(StatusAnuncio destino) {
        return destinosPermitidos().contains(destino);
    }

    private Set<StatusAnuncio> destinosPermitidos() {
        return switch (this) {
            case PRE_PUBLICACAO_SEM_PRECO -> EnumSet.of(PRE_PUBLICACAO_AGUARDANDO);
            case PRE_PUBLICACAO_AGUARDANDO -> EnumSet.of(PRONTO_PARA_PUBLICAR, PUBLICADO);
            case PRONTO_PARA_PUBLICAR -> EnumSet.of(PUBLICADO);
            case PUBLICADO -> EnumSet.of(EM_EDICAO);
            case EM_EDICAO -> EnumSet.of(PUBLICADO, REPUBLICACAO_AGENDADA);
            case REPUBLICACAO_AGENDADA -> EnumSet.of(PUBLICADO, EM_EDICAO);
        };
    }

    public boolean prePublicacao() {
        return this == PRE_PUBLICACAO_SEM_PRECO
                || this == PRE_PUBLICACAO_AGUARDANDO
                || this == PRONTO_PARA_PUBLICAR;
    }
}
