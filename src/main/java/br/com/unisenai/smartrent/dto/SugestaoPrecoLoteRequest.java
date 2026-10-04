package br.com.unisenai.smartrent.dto;

import java.util.List;

/** Imoveis em pre-publicacao para os quais o gestor quer sugestao de preco por IA. */
public record SugestaoPrecoLoteRequest(List<Long> imovelIds) {
}
