package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteRequest;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.TelemetriaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coleta publica da telemetria de uso (ADR-009). Aceita visitante e logado; o papel vem do token quando ha um, nunca
 * do corpo. Respeita os sinais "Do Not Track" e "Global Privacy Control": com eles, nada e gravado.
 */
@RestController
@RequestMapping("/api/telemetria")
public class TelemetriaController {

    private final TelemetriaService service;

    public TelemetriaController(TelemetriaService service) {
        this.service = service;
    }

    @PostMapping("/eventos")
    public ResponseEntity<LoteResponse> eventos(@RequestBody LoteRequest lote,
                                                @AuthenticationPrincipal Usuario usuario,
                                                HttpServletRequest http) {
        if ("1".equals(http.getHeader("DNT")) || "1".equals(http.getHeader("Sec-GPC"))) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new LoteResponse(0, 0));
        }
        String papel = usuario == null ? "VISITANTE" : usuario.getPapel().name();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.registrar(lote, papel, http.getRemoteAddr()));
    }
}
