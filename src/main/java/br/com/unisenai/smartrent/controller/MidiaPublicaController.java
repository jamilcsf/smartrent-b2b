package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.service.MidiaStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * Entrega os arquivos das midias. Sem autenticacao porque {@code <img>} e
 * {@code <video>} nao enviam o token; a seguranca esta na chave aleatoria
 * (UUID) da URL, que nao se adivinha. O suporte a Range (video) vem do Spring
 * quando o corpo e um {@link Resource}.
 */
@RestController
@RequestMapping("/api/midias")
public class MidiaPublicaController {

    private final ImovelMidiaRepository midiaRepository;
    private final MidiaStorage storage;

    public MidiaPublicaController(ImovelMidiaRepository midiaRepository, MidiaStorage storage) {
        this.midiaRepository = midiaRepository;
        this.storage = storage;
    }

    @GetMapping("/{chave}")
    public ResponseEntity<Resource> arquivo(@PathVariable String chave) {
        return midiaRepository.findByChave(chave)
                .filter(ImovelMidia::visivelAoPublico) // video enviando/processando/com falha nao e servido
                .map(m -> entregar(m.getArquivo(), m.getMime()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{chave}/poster")
    public ResponseEntity<Resource> poster(@PathVariable String chave) {
        return midiaRepository.findByChave(chave)
                .filter(ImovelMidia::visivelAoPublico)
                .filter(m -> m.getPoster() != null)
                .map((ImovelMidia m) -> entregar(m.getPoster(), "image/jpeg"))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Streaming adaptativo (HLS): lista mestre, listas de variante e segmentos de um video pronto. */
    @GetMapping("/{chave}/hls/{nome:[A-Za-z0-9_.-]+}")
    public ResponseEntity<Resource> hls(@PathVariable String chave, @PathVariable String nome) {
        if (nome.startsWith(".") || nome.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        return midiaRepository.findByChave(chave)
                .filter(ImovelMidia::visivelAoPublico)
                .filter(m -> m.getHlsMestre() != null)
                .map((ImovelMidia m) -> entregar("videos/" + chave + "/hls/" + nome, tipoHls(nome)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static String tipoHls(String nome) {
        if (nome.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        return nome.endsWith(".ts") ? "video/mp2t" : "application/octet-stream";
    }

    @GetMapping("/{chave}/miniatura")
    public ResponseEntity<Resource> miniatura(@PathVariable String chave) {
        return midiaRepository.findByChave(chave)
                .filter(m -> m.getMiniatura() != null)
                .map((ImovelMidia m) -> entregar(m.getMiniatura(), "image/jpeg"))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> entregar(String nome, String mime) {
        String direta = storage.urlPublica(nome);
        if (direta != null) { // CDN do bucket: os bytes nao passam pela aplicacao
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(direta))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                    .build();
        }
        Resource recurso = storage.abrir(nome);
        if (!recurso.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(recurso);
    }
}
