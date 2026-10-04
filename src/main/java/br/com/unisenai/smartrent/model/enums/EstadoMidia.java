package br.com.unisenai.smartrent.model.enums;

/**
 * Situacao da midia diante de uma edicao em andamento. Fora da edicao toda
 * midia e ATIVA. Durante ela, NOVA ainda nao pertence ao anuncio vivo e
 * REMOVIDA ainda pertence: so a confirmacao efetiva as duas, e o descarte as
 * desfaz sem deixar rastro.
 */
public enum EstadoMidia {
    ATIVA,
    NOVA,
    REMOVIDA
}
