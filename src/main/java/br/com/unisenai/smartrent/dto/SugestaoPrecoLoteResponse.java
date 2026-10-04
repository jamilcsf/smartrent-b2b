package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.StatusAnuncio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de uma sugestao em lote. Cada item e independente: a falha da IA
 * em um imovel nao derruba os demais, e o item com {@code erro} cai para a
 * edicao manual. Nada aqui foi salvo.
 */
public record SugestaoPrecoLoteResponse(List<Item> itens) {

    public record Item(
            Long imovelId,
            String titulo,
            StatusAnuncio status,
            BigDecimal valorAtual,
            BigDecimal valorSugerido,
            String justificativa,
            String modelo,
            String erro) {
    }
}
