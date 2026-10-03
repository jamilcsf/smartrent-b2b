package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.VideoUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * Envio de video em partes, retomavel (area do gestor; a propriedade do imovel e
 * conferida no servico). Fluxo: POST iniciar -> PUT partes (a partir de
 * {@code bytesRecebidos}) -> POST concluir (validacao no servidor e fila de
 * processamento) -> acompanhar {@code statusProcessamento} na listagem do anuncio.
 */
@RestController
@RequestMapping("/api/gestor/imoveis/{imovelId}/videos/uploads")
public class VideoGestorController {

    private final VideoUploadService service;

    public VideoGestorController(VideoUploadService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> iniciar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId,
                                       @RequestBody Map<String, Object> corpo) {
        long tamanho = corpo.get("tamanho") instanceof Number n ? n.longValue() : 0;
        String mime = corpo.get("tipo") instanceof String s ? s : null;
        MidiaResponse m = service.iniciar(gestor, imovelId, tamanho, mime);
        return Map.of("midia", m, "tamanhoParte", service.tamanhoDaParte());
    }

    @GetMapping("/{midiaId}")
    public MidiaResponse estado(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId, @PathVariable Long midiaId) {
        return service.estado(gestor, imovelId, midiaId);
    }

    @PutMapping(value = "/{midiaId}", consumes = "application/octet-stream")
    public MidiaResponse parte(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId, @PathVariable Long midiaId,
                               @RequestParam long offset, HttpServletRequest requisicao) throws IOException {
        return service.enviarParte(gestor, imovelId, midiaId, offset, requisicao.getInputStream());
    }

    @PostMapping("/{midiaId}/concluir")
    public MidiaResponse concluir(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId, @PathVariable Long midiaId) {
        return service.concluir(gestor, imovelId, midiaId);
    }

    @DeleteMapping("/{midiaId}")
    public ResponseEntity<Void> cancelar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long imovelId, @PathVariable Long midiaId) {
        service.cancelar(gestor, imovelId, midiaId);
        return ResponseEntity.noContent().build();
    }
}
