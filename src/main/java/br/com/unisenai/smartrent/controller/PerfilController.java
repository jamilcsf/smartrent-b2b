package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AtualizarPerfilRequest;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.dto.SenhaRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.FotoPerfilService;
import br.com.unisenai.smartrent.service.PerfilService;
import br.com.unisenai.smartrent.service.SenhaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Perfil do proprio usuario. Nenhum endpoint recebe id de usuario (nem na URL, nem no
 * corpo): tudo age sobre quem esta autenticado, o que elimina IDOR por construcao.
 */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final FotoPerfilService fotoService;
    private final SenhaService senhaService;

    public PerfilController(PerfilService perfilService, FotoPerfilService fotoService, SenhaService senhaService) {
        this.perfilService = perfilService;
        this.fotoService = fotoService;
        this.senhaService = senhaService;
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

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilResponse enviarFoto(@AuthenticationPrincipal Usuario usuario,
                                     @RequestParam("arquivo") MultipartFile arquivo,
                                     HttpServletRequest http) throws IOException {
        // Recusa pelo tamanho declarado antes de ler o arquivo para a memoria.
        if (arquivo.getSize() > fotoService.limiteBytes()) {
            throw new IllegalArgumentException(
                    "Use uma imagem PNG ou JPG de até " + fotoService.limiteBytes() / (1024 * 1024) + " MB.");
        }
        return fotoService.enviar(usuario, arquivo.getBytes(), http.getRemoteAddr());
    }

    @DeleteMapping("/foto")
    public PerfilResponse removerFoto(@AuthenticationPrincipal Usuario usuario, HttpServletRequest http) {
        return fotoService.remover(usuario, http.getRemoteAddr());
    }

    /** Troca a senha e devolve um token novo para esta sessao (as demais deixam de valer). */
    @PostMapping("/senha")
    public AuthResponse alterarSenha(@AuthenticationPrincipal Usuario usuario, @RequestBody SenhaRequest req,
                                     HttpServletRequest http) {
        return senhaService.alterar(usuario, req, http.getRemoteAddr());
    }
}
