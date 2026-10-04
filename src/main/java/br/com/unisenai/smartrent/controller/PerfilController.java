package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.PerfilService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
