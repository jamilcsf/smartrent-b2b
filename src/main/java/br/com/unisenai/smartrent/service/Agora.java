package br.com.unisenai.smartrent.service;

import java.time.Clock;
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
}
