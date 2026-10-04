package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.service.RefundPolicyService.Contexto;
import br.com.unisenai.smartrent.service.RefundPolicyService.Decisao;
import br.com.unisenai.smartrent.service.RefundPolicyService.Quem;
import br.com.unisenai.smartrent.service.RefundPolicyService.Regra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras de cancelamento e reembolso, decididas a partir do snapshot da reserva e
 * do horario de check-in em Brasilia (14:00). Sem banco e sem relogio de verdade.
 */
class RefundPolicyServiceTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate CHECKIN = LocalDate.of(2026, 11, 20);
    /** 14:00 de Brasilia de 18/11: exatamente 48h antes do check-in. */
    private static final Instant LIMITE = ZonedDateTime.of(2026, 11, 18, 14, 0, 0, 0, BRASILIA).toInstant();

    private final RefundPolicyService service =
            new RefundPolicyService(PoliticaCancelamentoProperties.padrao(), BRASILIA);

    private static Reserva reserva(StatusReserva status) {
        Reserva r = new Reserva();
        r.setId(1L);
        r.setStatus(status);
        r.setDataCheckin(CHECKIN);
        r.setDataCheckout(CHECKIN.plusDays(3));
        r.setTotalSnapshot(new BigDecimal("980.00"));
        r.setPoliticaAntecedenciaHoras(48);
        r.setPoliticaRegretDias(0);
        return r;
    }

    private Decisao decidir(Reserva r, Quem quem, Instant agora) {
        return service.decidir(new Contexto(r, quem, agora));
    }

    @Test
    @DisplayName("CT300 - Mais de 48h antes do check-in: reembolso integral (inclui a taxa de limpeza)")
    void maisDe48h() {
        Decisao d = decidir(reserva(StatusReserva.CONFIRMADA), Quem.CLIENTE, LIMITE.minusSeconds(3600 * 24));
        assertEquals(Regra.REEMBOLSO_INTEGRAL_ANTECEDENCIA, d.regra());
        assertEquals(StatusReserva.CANCELADA_COM_REEMBOLSO, d.statusFinal());
        assertEquals(new BigDecimal("980.00"), d.valor());
    }

    @Test
    @DisplayName("CT301 - Fronteira: exatamente 48h antes ainda reembolsa; 1 minuto depois nao")
    void fronteira48h() {
        Reserva r = reserva(StatusReserva.CONFIRMADA);
        assertTrue(decidir(r, Quem.CLIENTE, LIMITE.minusSeconds(60)).comReembolso(), "1 minuto antes do limite");
        assertTrue(decidir(r, Quem.CLIENTE, LIMITE).comReembolso(), "exatamente 48h antes");
        Decisao depois = decidir(r, Quem.CLIENTE, LIMITE.plusSeconds(60));
        assertFalse(depois.comReembolso(), "1 minuto depois do limite");
        assertEquals(Regra.SEM_REEMBOLSO_PRAZO, depois.regra());
        assertEquals(StatusReserva.CANCELADA_SEM_REEMBOLSO, depois.statusFinal());
        assertEquals(0, BigDecimal.ZERO.compareTo(depois.valor()));
    }

    @Test
    @DisplayName("CT302 - O prazo conta 48h antes do HORARIO de check-in em Brasilia, nao dias de calendario")
    void prazoEmHorasDeBrasilia() {
        // 14:00 de Brasilia (UTC-3) = 17:00 UTC. 13:59 do dia 18 ainda reembolsa; 14:01 nao.
        assertEquals(Instant.parse("2026-11-18T17:00:00Z"), service.limiteDoReembolso(reserva(StatusReserva.CONFIRMADA)));
        Reserva r = reserva(StatusReserva.CONFIRMADA);
        assertTrue(decidir(r, Quem.CLIENTE, Instant.parse("2026-11-18T16:59:00Z")).comReembolso());
        assertFalse(decidir(r, Quem.CLIENTE, Instant.parse("2026-11-18T17:01:00Z")).comReembolso());
    }

    @Test
    @DisplayName("CT303 - Reserva criada com menos de 48h do check-in nasce sem direito a reembolso")
    void criadaComMenosDe48h() {
        Instant agora = LIMITE.plusSeconds(3600 * 20); // faltam ~28h para o check-in
        Decisao d = decidir(reserva(StatusReserva.CONFIRMADA), Quem.CLIENTE, agora);
        assertFalse(d.comReembolso());
        assertEquals(Regra.SEM_REEMBOLSO_PRAZO, d.regra());
    }

    @Test
    @DisplayName("CT304 - Cancelamento pelo gestor: reembolso integral sempre, mesmo depois do prazo")
    void gestorSempreIntegral() {
        Decisao d = decidir(reserva(StatusReserva.CONFIRMADA), Quem.GESTOR, LIMITE.plusSeconds(3600 * 40));
        assertEquals(Regra.GESTOR_CANCELA, d.regra());
        assertEquals(StatusReserva.CANCELADA_PELO_GESTOR, d.statusFinal());
        assertEquals(new BigDecimal("980.00"), d.valor());
        assertTrue(d.comReembolso());
    }

    @Test
    @DisplayName("CT305 - Reserva pendente cancela sem cobranca, em qualquer momento")
    void pendenteSemCobranca() {
        Decisao d = decidir(reserva(StatusReserva.PENDENTE), Quem.CLIENTE, LIMITE.plusSeconds(3600 * 40));
        assertEquals(Regra.PENDENTE_SEM_COBRANCA, d.regra());
        assertFalse(d.comReembolso());
        assertEquals(StatusReserva.CANCELADA_SEM_REEMBOLSO, d.statusFinal());
    }

    @Test
    @DisplayName("CT306 - Os parametros do snapshot valem, nao a configuracao atual (outra antecedencia gravada na reserva)")
    void valeOSnapshot() {
        Reserva r = reserva(StatusReserva.CONFIRMADA);
        r.setPoliticaAntecedenciaHoras(24); // reserva criada quando a politica era de 24h
        Instant limite24 = ZonedDateTime.of(2026, 11, 19, 14, 0, 0, 0, BRASILIA).toInstant();
        // servico configurado com 48h hoje, mas a reserva guarda 24h
        assertTrue(decidir(r, Quem.CLIENTE, limite24.minusSeconds(60)).comReembolso());
        assertFalse(decidir(r, Quem.CLIENTE, limite24.plusSeconds(60)).comReembolso());
    }

    @Test
    @DisplayName("CT307 - CANCEL_REGRET_DAYS com o padrao 0 nao altera nenhuma decisao")
    void padraoDoPrazoDeArrependimentoNaoAlteraDecisoes() {
        assertEquals(0, PoliticaCancelamentoProperties.padrao().regretDias());
        Reserva sem = reserva(StatusReserva.CONFIRMADA);
        Reserva zero = reserva(StatusReserva.CONFIRMADA);
        zero.setPoliticaRegretDias(0);
        for (long deslocamento : new long[]{-86400L * 5, -3600, 0, 3600, 86400L}) {
            Instant agora = LIMITE.plusSeconds(deslocamento);
            assertEquals(decidir(sem, Quem.CLIENTE, agora), decidir(zero, Quem.CLIENTE, agora));
        }
    }

    @Test
    @DisplayName("CT308 - Horario de check-in configuravel muda o limite (ex.: 12:00)")
    void horarioDeCheckinConfiguravel() {
        var props = new PoliticaCancelamentoProperties(48, 0, "PROVISORIA-1", LocalTime.of(12, 0), LocalTime.of(10, 0));
        var s = new RefundPolicyService(props, BRASILIA);
        assertEquals(Instant.parse("2026-11-18T15:00:00Z"), s.limiteDoReembolso(reserva(StatusReserva.CONFIRMADA)));
    }
}
