package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioPedido;
import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioResposta;
import br.com.unisenai.smartrent.dto.CalendarioDtos.Calendario;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.BloqueioService;
import br.com.unisenai.smartrent.service.CalendarioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendario do gestor e bloqueio manual de datas. Restrito a gestores
 * (SecurityConfig); os servicos conferem que o imovel e de quem pede.
 */
@RestController
@RequestMapping("/api/gestor")
public class CalendarioController {

    private final CalendarioService calendarioService;
    private final BloqueioService bloqueioService;

    public CalendarioController(CalendarioService calendarioService, BloqueioService bloqueioService) {
        this.calendarioService = calendarioService;
        this.bloqueioService = bloqueioService;
    }

    @GetMapping("/calendario")
    public Calendario calendario(@AuthenticationPrincipal Usuario gestor,
                                 @RequestParam Long imovelId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return calendarioService.calendario(gestor, imovelId, de, ate);
    }

    @GetMapping("/imoveis/{imovelId}/bloqueios")
    public List<BloqueioResposta> listar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId) {
        return bloqueioService.listar(gestor, imovelId);
    }

    @PostMapping("/imoveis/{imovelId}/bloqueios")
    public List<BloqueioResposta> criar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId,
                                        @RequestBody BloqueioPedido pedido) {
        return bloqueioService.criar(gestor, imovelId, pedido);
    }

    /** Sem {@code de}/{@code ate} remove o bloqueio inteiro; com eles, apenas essa parte (o resto fica). */
    @DeleteMapping("/imoveis/{imovelId}/bloqueios/{bloqueioId}")
    public List<BloqueioResposta> remover(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId,
                                          @PathVariable Long bloqueioId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return bloqueioService.remover(gestor, imovelId, bloqueioId, de, ate);
    }
}
