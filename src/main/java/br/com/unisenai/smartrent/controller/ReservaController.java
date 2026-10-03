package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.dto.ReservaResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestao de reservas. Restrita a gestores (SecurityConfig) e, dentro dela, ao
 * que pertence aos imoveis de quem pede (ReservaService).
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @GetMapping
    public List<ReservaResponse> listar(@AuthenticationPrincipal Usuario gestor) {
        return reservaService.listar(gestor).stream().map(ReservaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public ReservaResponse buscarPorId(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id) {
        return ReservaResponse.de(reservaService.buscar(gestor, id));
    }

    @PostMapping
    public ReservaResponse criar(@AuthenticationPrincipal Usuario gestor, @RequestBody ReservaRequest req) {
        return ReservaResponse.de(reservaService.criar(gestor, req));
    }

    @PutMapping("/{id}")
    public ReservaResponse atualizar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                     @RequestBody ReservaRequest req) {
        return ReservaResponse.de(reservaService.atualizar(gestor, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id) {
        reservaService.excluir(gestor, id);
        return ResponseEntity.noContent().build();
    }
}
