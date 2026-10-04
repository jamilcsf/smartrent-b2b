package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AtualizarPerfilRequest;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.PerfilService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Perfil do proprio usuario. Nenhum endpoint recebe id de usuario (nem na URL, nem no
 * corpo): tudo age sobre quem esta autenticado, o que elimina IDOR por construcao.
 */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    public PerfilResponse obter(@AuthenticationPrincipal Usuario usuario) {
        return perfilService.obter(usuario);
    }

    @PatchMapping
    public PerfilResponse atualizar(@AuthenticationPrincipal Usuario usuario,
                                    @RequestBody AtualizarPerfilRequest req, HttpServletRequest http) {
        return perfilService.atualizarNome(usuario, req.nome(), http.getRemoteAddr());
    }
}
