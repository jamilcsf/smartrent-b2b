package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(origins = "*")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<?> criarReserva(@RequestBody Reserva reserva) {
        try {
            Reserva novaReserva = reservaService.cadastrarReserva(reserva);
            return ResponseEntity.ok(novaReserva);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}