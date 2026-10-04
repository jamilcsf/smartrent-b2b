package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.service.FotoPerfilService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Entrega publica das fotos de perfil (avatar aparece a outros usuarios e em tags img, que
 * nao enviam o token). A protecao esta no nome aleatorio de 128 bits; o servico so aceita
 * o formato exato que ele mesmo gera. Como a URL muda a cada troca (?v=), o cache e longo.
 */
@RestController
@RequestMapping("/api/perfil/foto")
public class FotoPerfilController {

    private final FotoPerfilService fotoService;

    public FotoPerfilController(FotoPerfilService fotoService) {
        this.fotoService = fotoService;
    }

    @GetMapping("/{arquivo}")
    public ResponseEntity<Resource> foto(@PathVariable String arquivo) {
        Resource recurso = fotoService.abrir(arquivo);
        if (recurso == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(recurso);
    }
}
