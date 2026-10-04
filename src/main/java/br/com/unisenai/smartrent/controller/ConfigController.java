package br.com.unisenai.smartrent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Relogio da plataforma para o front: "hoje" e "mes atual" sao decididos aqui,
 * em horario de Brasilia, e nao pelo relogio ou fuso do navegador.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final Clock clock;
    private final ZoneId zona;

    public ConfigController(Clock clock, ZoneId zonaDaPlataforma) {
        this.clock = clock;
        this.zona = zonaDaPlataforma;
    }

    @GetMapping("/agora")
    public Map<String, String> agora() {
        ZonedDateTime agora = ZonedDateTime.now(clock);
        return Map.of(
                "instante", agora.toInstant().toString(),
                "agora", agora.toLocalDateTime().toString(),
                "hoje", agora.toLocalDate().toString(),
                "mes", agora.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                "zona", zona.getId());
    }
}
