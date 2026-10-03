package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.NotificacaoResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.NotificacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Notificacoes in-app do gestor (sino do dashboard). */
@RestController
@RequestMapping("/api/gestor/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public Map<String, Object> listar(@AuthenticationPrincipal Usuario usuario) {
        List<NotificacaoResponse> itens = notificacaoService.listar(usuario);
        return Map.of("naoLidas", notificacaoService.naoLidas(usuario), "itens", itens);
    }

    @PostMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        notificacaoService.marcarLida(usuario, id);
        return ResponseEntity.noContent().build();
    }
}
