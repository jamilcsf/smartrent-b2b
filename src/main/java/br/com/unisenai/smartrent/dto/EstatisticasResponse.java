package br.com.unisenai.smartrent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Estatisticas do gestor autenticado (Dashboard). So agrega; nao tem acao.
 *
 * <p>Ocupacao: noites com reserva ativa (confirmada ou concluida) sobre as
 * noites disponiveis; noites bloqueadas manualmente saem do denominador e sao
 * informadas a parte. Reservas canceladas nunca contam como ocupacao.
 * Receita liquida = receita das reservas pagas menos reembolsos processados.
 */
public record EstatisticasResponse(
        LocalDate de,
        LocalDate ate,
        long totalImoveis,
        Map<String, Long> imoveisPorStatus,
        long totalReservas,
        long reservasCanceladas,
        long noitesOcupadas,
        long noitesBloqueadas,
        long noitesDisponiveis,
        double taxaOcupacao,
        BigDecimal receitaBruta,
        BigDecimal reembolsos,
        BigDecimal receitaLiquida,
        BigDecimal ticketMedio,
        List<Mes> meses) {

    public record Mes(String mes, long reservas, long canceladas, long noitesOcupadas,
                      BigDecimal receitaBruta, BigDecimal reembolsos, BigDecimal receitaLiquida) {
    }
}
