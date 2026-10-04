package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contratos da reserva feita pelo cliente (autoatendimento). */
public final class ReservaClienteDtos {

    private ReservaClienteDtos() {
    }

    /** Pedido de previa ou de criacao. {@code cienteSemReembolso} so conta na criacao. */
    public record Pedido(
            Long imovelId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            Integer numeroHospedes,
            String observacoes,
            Boolean cienteSemReembolso) {
    }

    public record PagamentoPedido(String token) {
    }

    /**
     * Politica de cancelamento que vale para a reserva. O texto completo e
     * provisorio (pendente de revisao juridica) e vem de {@code textoUrl}.
     */
    public record Politica(
            String versao,
            int antecedenciaHoras,
            Instant reembolsoIntegralAte,
            boolean semDireitoAReembolso,
            String horarioCheckin,
            String fuso,
            String textoUrl) {
    }

    /** Valores e regras antes de reservar: o que sera cobrado e se havera direito a reembolso. */
    public record Previa(
            Long imovelId,
            String imovelCodigo,
            String imovelTitulo,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            int numeroDiarias,
            int numeroHospedes,
            int minimoDiarias,
            int limiteHospedes,
            BigDecimal precoDiaria,
            BigDecimal subtotalDiarias,
            BigDecimal taxaLimpeza,
            BigDecimal total,
            String moeda,
            Politica politica) {
    }

    /** Reserva como o proprio cliente a ve (sem dados de outros hospedes nem do gestor alem do necessario). */
    public record Resposta(
            Long id,
            Long imovelId,
            String imovelCodigo,
            String imovelTitulo,
            String imovelEndereco,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            int numeroDiarias,
            int numeroHospedes,
            BigDecimal precoDiaria,
            BigDecimal taxaLimpeza,
            BigDecimal total,
            String moeda,
            StatusReserva status,
            LocalDateTime criadaEm,
            LocalDateTime expiraEm,
            boolean podePagar,
            boolean podeCancelar,
            Politica politica,
            Cancelamento cancelamento) {
    }

    /** Resultado do cancelamento ja efetivado (ou nulo enquanto a reserva nao foi cancelada). */
    public record Cancelamento(
            Instant canceladaEm,
            String canceladaPor,
            String regra,
            String motivo,
            String reembolsoStatus,
            BigDecimal reembolsoValor,
            String prazoReembolso) {
    }

    /** Simulacao exibida antes de confirmar o cancelamento: a decisao final e recalculada no servidor. */
    public record Simulacao(
            Long reservaId,
            String regra,
            boolean comReembolso,
            BigDecimal valorReembolso,
            String mensagem,
            Instant reembolsoIntegralAte,
            Instant calculadoEm) {
    }

    public record CancelamentoPedido(String motivo) {
    }
}
