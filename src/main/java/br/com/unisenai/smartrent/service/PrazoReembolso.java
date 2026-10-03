package br.com.unisenai.smartrent.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Prazo do reembolso integral: ate {@code antecedenciaHoras} antes do HORARIO de
 * check-in do imovel (padrao 14:00 de Brasilia), e nao em dias de calendario.
 * "Ate" inclui o instante exato: cancelar exatamente 48h antes ainda reembolsa.
 */
public final class PrazoReembolso {

    private PrazoReembolso() {
    }

    public static Instant limite(LocalDate checkin, LocalTime horaCheckin, int antecedenciaHoras, ZoneId zona) {
        return ZonedDateTime.of(checkin, horaCheckin, zona).minusHours(antecedenciaHoras).toInstant();
    }

    public static boolean dentroDoPrazo(Instant agora, Instant limite) {
        return !agora.isAfter(limite);
    }
}
