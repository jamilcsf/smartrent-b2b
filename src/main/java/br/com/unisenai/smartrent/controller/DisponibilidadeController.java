package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.CalendarioDtos.Faixa;
import br.com.unisenai.smartrent.service.CalendarioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Disponibilidade publica de um anuncio publicado. Reservas e bloqueios manuais
 * saem juntos como "indisponivel": o cliente nao sabe a origem nem o motivo.
 */
@RestController
@RequestMapping("/api/imoveis")
public class DisponibilidadeController {

    private final CalendarioService calendarioService;

    public DisponibilidadeController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping("/{id}/indisponibilidade")
    public List<Faixa> indisponibilidade(@PathVariable Long id,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return calendarioService.indisponibilidade(id, de, ate);
    }
}
