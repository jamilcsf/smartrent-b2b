package br.com.unisenai.smartrent.controller;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** "Hoje" e "mes atual" seguem Brasilia, nao o fuso do servidor nem do navegador. */
class ConfigControllerTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private static java.util.Map<String, String> em(String instanteUtc) {
        Clock relogio = Clock.fixed(Instant.parse(instanteUtc), BRASILIA);
        return new ConfigController(relogio, BRASILIA).agora();
    }

    @Test
    void viradaDeDiaAcontecePorBrasiliaEmUtc0300() {
        // 02:59 UTC ainda e 23:59 do dia 3 em Brasilia; 03:00 UTC ja e dia 4.
        assertThat(em("2026-10-04T02:59:00Z").get("hoje")).isEqualTo("2026-10-03");
        assertThat(em("2026-10-04T03:00:00Z").get("hoje")).isEqualTo("2026-10-04");
    }

    @Test
    void viradaDeMesPorBrasilia() {
        assertThat(em("2026-11-01T02:59:59Z").get("mes")).isEqualTo("2026-10");
        assertThat(em("2026-11-01T03:00:00Z").get("mes")).isEqualTo("2026-11");
    }

    @Test
    void informaZonaEInstanteUtc() {
        var r = em("2026-10-04T03:00:00Z");
        assertThat(r.get("zona")).isEqualTo("America/Sao_Paulo");
        assertThat(r.get("instante")).isEqualTo("2026-10-04T03:00:00Z");
        assertThat(r.get("agora")).isEqualTo("2026-10-04T00:00");
    }
}
