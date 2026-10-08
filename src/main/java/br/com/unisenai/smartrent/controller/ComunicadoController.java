package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ModeracaoDtos.ComunicadoRecebido;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ComunicadoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Avisos da administracao para o proprio usuario logado (qualquer papel). */
@RestController
@RequestMapping("/api/comunicados")
public class ComunicadoController {

    private final ComunicadoService service;

    public ComunicadoController(ComunicadoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ComunicadoRecebido> listar(@AuthenticationPrincipal Usuario usuario) {
        return service.listar(usuario);
    }

    @GetMapping("/nao-lidos")
    public Map<String, Long> naoLidos(@AuthenticationPrincipal Usuario usuario) {
        return Map.of("total", service.naoLidos(usuario));
    }

    @PostMapping("/{id}/lido")
    public Map<String, Boolean> lido(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        service.marcarLido(usuario, id);
        return Map.of("ok", true);
    }
}
