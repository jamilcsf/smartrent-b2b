package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.OrigemPreco;

import java.math.BigDecimal;

/** Preco definido pelo gestor. {@code origem} registra se veio de sugestao de IA. */
public record PrecoRequest(BigDecimal valor, OrigemPreco origem) {
}
