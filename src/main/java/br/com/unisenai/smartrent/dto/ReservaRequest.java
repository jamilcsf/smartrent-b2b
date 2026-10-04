package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados aceitos para criar ou atualizar uma reserva.
 *
 * <p>Expor um Record em vez da entidade (RNF08) mantem o contrato da API
 * estavel mesmo quando o mapeamento muda, e evita que o cliente precise
 * conhecer o grafo de objetos: o imovel entra por identificador.
 */
public record ReservaRequest(
        Long imovelId,
        String hospedeNome,
        String hospedeEmail,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
        /** Ignorado pelo servidor: o total sai do snapshot de preco, nunca do cliente. */
        BigDecimal valorTotal,
        StatusReserva status,
        OrigemReserva origem,
        String observacoes,
        /** Quantidade de hospedes; vazio vale 1. Nunca pode passar do limite do imovel. */
        Integer numeroHospedes) {

    /** Forma sem numero de hospedes (assume 1), mantida para chamadores antigos. */
    public ReservaRequest(Long imovelId, String hospedeNome, String hospedeEmail,
                          LocalDate dataCheckin, LocalDate dataCheckout, BigDecimal valorTotal,
                          StatusReserva status, OrigemReserva origem, String observacoes) {
        this(imovelId, hospedeNome, hospedeEmail, dataCheckin, dataCheckout, valorTotal,
                status, origem, observacoes, null);
    }
}
