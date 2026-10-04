package br.com.unisenai.smartrent.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * "Agora" com a precisao do banco (microssegundos). Sem isso, o valor devolvido
 * logo apos gravar (nanossegundos, em memoria) difere do que o banco guarda e
 * devolve depois, e comparacoes de instantes entre respostas falham por ruido.
 */
final class Agora {

    private Agora() {
    }

    static LocalDateTime de(Clock clock) {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Soma uma DURACAO (tempo decorrido) a um horario local da plataforma. Prazos de
     * 24h, 2h, lembretes etc. sao absolutos: se um dia tiver 23 ou 25 horas (mudanca de
     * regra de horario de verao), a conta continua certa, porque passa pelo instante.
     */
    static LocalDateTime somar(LocalDateTime base, Duration duracao, Clock clock) {
        return base.atZone(clock.getZone()).plus(duracao).toLocalDateTime();
    }

    /** Tempo realmente decorrido entre dois horarios locais da plataforma. */
    static Duration decorrido(LocalDateTime de, LocalDateTime ate, Clock clock) {
        return Duration.between(de.atZone(clock.getZone()), ate.atZone(clock.getZone()));
    }
}
