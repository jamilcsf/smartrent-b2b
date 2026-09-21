package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Representacao de uma reserva devolvida pela API. */
public record ReservaResponse(
        Long id,
        Long imovelId,
        String hospedeNome,
        String hospedeEmail,
        String hospedeTelefone,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
        BigDecimal valorTotal,
        StatusReserva status,
        OrigemReserva origem,
        String observacoes,
        LocalDateTime dataCriacao) {

    public static ReservaResponse de(Reserva r) {
        return new ReservaResponse(
                r.getId(),
                r.getImovel() == null ? null : r.getImovel().getId(),
                r.getHospedeNome(),
                r.getHospedeEmail(),
                r.getHospedeTelefone(),
                r.getDataCheckin(),
                r.getDataCheckout(),
                r.getValorTotal(),
                r.getStatus(),
                r.getOrigem(),
                r.getObservacoes(),
                r.getDataCriacao());
    }
}
