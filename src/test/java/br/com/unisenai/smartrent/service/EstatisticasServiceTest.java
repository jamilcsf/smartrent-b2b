package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.EstatisticasResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstatisticasServiceTest {

    private static Imovel imovel(StatusAnuncio status) {
        Imovel i = new Imovel();
        i.setStatus(status);
        return i;
    }

    private static Reserva reserva(StatusReserva status, String in, String out, String total) {
        Reserva r = new Reserva();
        r.setStatus(status);
        r.setDataCheckin(LocalDate.parse(in));
        r.setDataCheckout(LocalDate.parse(out));
        r.setTotalSnapshot(new BigDecimal(total));
        return r;
    }

    @Test
    @DisplayName("CT110 - Cancelada nao conta como ocupacao nem receita; pendente tambem nao")
    void canceladaEPendenteNaoOcupam() {
        var imoveis = List.of(imovel(StatusAnuncio.PUBLICADO));
        var reservas = List.of(
                reserva(StatusReserva.CONFIRMADA, "2026-10-10", "2026-10-13", "900.00"),
                reserva(StatusReserva.CANCELADA, "2026-10-20", "2026-10-25", "1500.00"),
                reserva(StatusReserva.PENDENTE, "2026-10-26", "2026-10-28", "600.00"));

        EstatisticasResponse e = EstatisticasService.agregar(imoveis, reservas,
                YearMonth.of(2026, 10), YearMonth.of(2026, 10));

        assertThat(e.noitesOcupadas()).isEqualTo(3);
        assertThat(e.noitesDisponiveis()).isEqualTo(31);
        assertThat(e.taxaOcupacao()).isEqualTo(3.0 / 31);
        assertThat(e.receitaBruta()).isEqualByComparingTo("900.00");
        assertThat(e.reservasCanceladas()).isEqualTo(1);
        assertThat(e.totalReservas()).isEqualTo(2);
        assertThat(e.imoveisPorStatus().get("PUBLICADO")).isEqualTo(1);
    }

    @Test
    @DisplayName("CT111 - Reserva que cruza a virada de mes divide as noites entre os meses")
    void reservaCruzandoMes() {
        var reservas = List.of(reserva(StatusReserva.CONFIRMADA, "2026-10-30", "2026-11-02", "600.00"));
        EstatisticasResponse e = EstatisticasService.agregar(List.of(imovel(StatusAnuncio.PUBLICADO)), reservas,
                YearMonth.of(2026, 10), YearMonth.of(2026, 11));

        assertThat(e.meses()).hasSize(2);
        assertThat(e.meses().get(0).noitesOcupadas()).isEqualTo(2); // 30 e 31/10
        assertThat(e.meses().get(1).noitesOcupadas()).isEqualTo(1); // 01/11
        assertThat(e.meses().get(0).receitaBruta()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("CT112 - Usuario sem papel de gestor nao calcula estatisticas")
    void clienteNaoCalcula() {
        var service = new EstatisticasService(mock(ImovelRepository.class), mock(ReservaRepository.class),
                new ImovelAcesso(mock(ImovelRepository.class)), Clock.systemUTC());
        Usuario cliente = new Usuario();
        cliente.setId(1L);
        cliente.setPapel(PapelUsuario.CLIENTE);

        assertThatThrownBy(() -> service.calcular(cliente, 6)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("CT113 - Consulta so os imoveis e reservas do gestor autenticado e usa o mes de Brasilia")
    void filtraPeloGestorEUsaBrasilia() {
        ImovelRepository imoveis = mock(ImovelRepository.class);
        ReservaRepository reservas = mock(ReservaRepository.class);
        when(imoveis.findByUsuarioId(7L)).thenReturn(List.of());
        when(reservas.findByImovelUsuarioIdOrderByDataCheckinDesc(7L)).thenReturn(List.of());
        // 02:00 UTC de 01/11 ainda e 31/10 em Brasilia: o periodo termina em outubro.
        Clock relogio = Clock.fixed(Instant.parse("2026-11-01T02:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var service = new EstatisticasService(imoveis, reservas, new ImovelAcesso(imoveis), relogio);
        Usuario gestor = new Usuario();
        gestor.setId(7L);
        gestor.setPapel(PapelUsuario.ANFITRIAO);

        EstatisticasResponse e = service.calcular(gestor, 1);

        verify(imoveis).findByUsuarioId(7L);
        verify(reservas).findByImovelUsuarioIdOrderByDataCheckinDesc(7L);
        assertThat(e.ate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }
}
