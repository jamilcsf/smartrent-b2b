package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.BloqueioData;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos do calendario do gestor e do bloqueio manual de datas. */
public final class CalendarioDtos {

    private CalendarioDtos() {
    }

    public record ImovelResumo(Long id, String codigo, String titulo) {
    }

    /**
     * Reserva no calendario. Os dados do hospede vem do snapshot da reserva e so
     * saem para o gestor dono do imovel.
     */
    public record ReservaItem(
            Long id,
            StatusReserva status,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            String hospedeNome,
            String hospedeEmail,
            int numeroHospedes,
            boolean chatDisponivel) {
    }

    public record BloqueioResposta(
            Long id,
            Long imovelId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            String motivo,
            String observacao,
            Instant criadoEm) {

        public static BloqueioResposta de(BloqueioData b) {
            return new BloqueioResposta(b.getId(), b.getImovel().getId(), b.getDataInicio(), b.getDataFim(),
                    b.getMotivo(), b.getObservacao(), b.getCriadoEm());
        }
    }

    /** {@code hoje} e a data em horario de Brasilia, decidida pelo servidor. */
    public record Calendario(
            ImovelResumo imovel,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate de,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate ate,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate hoje,
            String zona,
            List<ReservaItem> reservas,
            List<BloqueioResposta> bloqueios) {
    }

    public record BloqueioPedido(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            String motivo,
            String observacao,
            Boolean apenasLivres,
            Boolean cancelarPendentes) {
    }

    /** Faixa de noites indisponiveis para clientes (reserva ou bloqueio; a origem nunca e revelada). */
    public record Faixa(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate inicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fim) {
    }
}
