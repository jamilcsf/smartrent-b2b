package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representacao de uma reserva devolvida pela API. {@code valorTotal} e o
 * total vigente (so muda se as datas mudarem, sempre com a diaria do
 * snapshot); os demais campos de snapshot nunca mudam depois da criacao.
 */
public record ReservaResponse(
        Long id,
        Long imovelId,
        String hospedeNome,
        String hospedeEmail,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
        BigDecimal valorTotal,
        StatusReserva status,
        OrigemReserva origem,
        String observacoes,
        LocalDateTime dataCriacao,
        /* Snapshot das condicoes no momento da criacao: e o que vale para cobranca e relatorios. */
        String moeda,
        int numeroDiarias,
        BigDecimal precoDiaria,
        BigDecimal taxas,
        BigDecimal totalOriginal,
        String imovelTitulo,
        String imovelEndereco,
        String imovelCaracteristicas,
        int numeroHospedes,
        int minimoDiarias,
        BigDecimal taxaLimpeza,
        int limiteHospedes) {

    public static ReservaResponse de(Reserva r) {
        return new ReservaResponse(
                r.getId(),
                r.getImovel() == null ? null : r.getImovel().getId(),
                r.getHospedeNome(),
                r.getHospedeEmail(),
                r.getDataCheckin(),
                r.getDataCheckout(),
                r.getValorTotal(),
                r.getStatus(),
                r.getOrigem(),
                r.getObservacoes(),
                r.getDataCriacao(),
                r.getMoeda(),
                r.getNumeroDiarias(),
                r.getPrecoDiariaSnapshot(),
                r.getTaxasSnapshot(),
                r.getTotalSnapshot(),
                r.getImovelTituloSnapshot(),
                r.getImovelEnderecoSnapshot(),
                r.getImovelCaracteristicasSnapshot(),
                r.getNumeroHospedes(),
                r.getMinimoDiariasSnapshot(),
                r.getTaxaLimpezaSnapshot(),
                r.getLimiteHospedesSnapshot());
    }
}
