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
        String hospedeTelefone,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
        BigDecimal valorTotal,
        StatusReserva status,
        OrigemReserva origem,
        String observacoes) {
}
