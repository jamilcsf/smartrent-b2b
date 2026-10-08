package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos do painel de admin. Somente leitura, exceto ativar/desativar usuario. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record DiaTotal(LocalDate dia, long total) {
    }

    public record VisaoGeral(int dias, long totalUsuarios, long usuariosInativos, long novosUsuarios,
                             List<ContagemRotulo> usuariosPorPapel, long totalImoveis,
                             List<ContagemRotulo> imoveisPorStatus, long totalReservas, long reservasNoPeriodo,
                             List<ContagemRotulo> reservasPorStatus, BigDecimal valorMovimentado,
                             long solicitacoesExclusaoAbertas, long denunciasChatPendentes,
                             long sessoesNoDetalhe, double taxaConversao,
                             List<DiaTotal> cadastrosPorDia, List<DiaTotal> reservasPorDia) {
    }

    public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {
    }

    public record UsuarioAdmin(Long id, String nome, String email, String papel, boolean ativo,
                               boolean emailVerificado, LocalDateTime criadoEm) {
    }

    public record ImovelAdmin(Long id, String titulo, String local, String gestor, String status, boolean ativo,
                              BigDecimal diaria, LocalDateTime publicadoEm, LocalDateTime cadastradoEm) {
    }

    public record ReservaAdmin(Long id, String imovel, String hospede, LocalDate checkin, LocalDate checkout,
                               String status, String origem, BigDecimal total, LocalDateTime criadaEm) {
    }
}
