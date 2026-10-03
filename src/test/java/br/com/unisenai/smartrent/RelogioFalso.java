package br.com.unisenai.smartrent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relogio controlavel para testar regras de prazo (24h, 2h, lembretes) sem
 * esperar: o teste simplesmente avanca o tempo.
 */
public class RelogioFalso extends Clock {

    private Instant agora;

    public RelogioFalso(LocalDateTime inicio) {
        this.agora = inicio.toInstant(ZoneOffset.UTC);
    }

    public void avancar(Duration d) {
        agora = agora.plus(d);
    }

    public LocalDateTime agora() {
        return LocalDateTime.ofInstant(agora, ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
