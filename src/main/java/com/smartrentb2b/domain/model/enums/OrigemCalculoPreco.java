package com.smartrentb2b.domain.model.enums;

/**
 * Indica se uma {@code SugestaoPreco} foi gerada pela IA generativa (Groq /
 * Llama 3.3) ou pela regra de negócio determinística de fallback, garantindo
 * auditabilidade do requisito Must Have "Sugestão IA com fallback".
 */
public enum OrigemCalculoPreco {
    IA_GENERATIVA,
    FALLBACK_REGRA_NEGOCIO
}