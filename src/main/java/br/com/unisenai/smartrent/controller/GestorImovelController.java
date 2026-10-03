package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AceiteRequest;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.AnuncioGestorResponse;
import br.com.unisenai.smartrent.dto.ConfirmacaoRequest;
import br.com.unisenai.smartrent.dto.CriarAnuncioRequest;
import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.dto.OrdemMidiasRequest;
import br.com.unisenai.smartrent.dto.PrecoRequest;
import br.com.unisenai.smartrent.dto.TipoMidiaRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.service.AnuncioEdicaoService;
import br.com.unisenai.smartrent.service.AnuncioService;
import br.com.unisenai.smartrent.service.MidiaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Anuncios do gestor. Todo o prefixo {@code /api/gestor/**} exige papel de
 * gestor (ver SecurityConfig); a propriedade de cada imovel e conferida nos
 * servicos.
 */
@RestController
@RequestMapping("/api/gestor/imoveis")
public class GestorImovelController {

    private final AnuncioService anuncioService;
    private final AnuncioEdicaoService edicaoService;
    private final MidiaService midiaService;

    public GestorImovelController(AnuncioService anuncioService,
                                  AnuncioEdicaoService edicaoService,
                                  MidiaService midiaService) {
        this.anuncioService = anuncioService;
        this.edicaoService = edicaoService;
        this.midiaService = midiaService;
    }

    @PostMapping
    public ResponseEntity<AnuncioGestorResponse> criar(@AuthenticationPrincipal Usuario gestor,
                                                       @Valid @RequestBody CriarAnuncioRequest req,
                                                       HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(anuncioService.criar(gestor, req, http.getRemoteAddr()));
    }

    @GetMapping
    public List<AnuncioGestorResponse> listar(@AuthenticationPrincipal Usuario gestor) {
        return anuncioService.listarMeus(gestor);
    }

    @GetMapping("/{id}")
    public AnuncioGestorResponse obter(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id) {
        return anuncioService.obter(gestor, id);
    }

    /** Edicao direta, valida so em pre-publicacao. */
    @PutMapping("/{id}")
    public AnuncioGestorResponse atualizar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                           @Valid @RequestBody AnuncioDados dados) {
        return anuncioService.atualizar(gestor, id, dados);
    }

    // ---------------------------------------------------------------- midias

    @PostMapping(path = "/{id}/midias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MidiaResponse> enviarMidia(@AuthenticationPrincipal Usuario gestor,
                                                     @PathVariable Long id,
                                                     @RequestParam("arquivo") MultipartFile arquivo,
                                                     @RequestParam("tipo") TipoMidia tipo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(midiaService.adicionar(gestor, id, arquivo, tipo));
    }

    @DeleteMapping("/{id}/midias/{midiaId}")
    public ResponseEntity<Void> removerMidia(@AuthenticationPrincipal Usuario gestor,
                                             @PathVariable Long id, @PathVariable Long midiaId) {
        midiaService.remover(gestor, id, midiaId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/midias/ordem")
    public ResponseEntity<Void> ordenarMidias(@AuthenticationPrincipal Usuario gestor,
                                              @PathVariable Long id, @RequestBody OrdemMidiasRequest req) {
        midiaService.ordenar(gestor, id, req.ids());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/midias/{midiaId}/capa")
    public ResponseEntity<Void> definirCapa(@AuthenticationPrincipal Usuario gestor,
                                            @PathVariable Long id, @PathVariable Long midiaId) {
        midiaService.definirCapa(gestor, id, midiaId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/midias/{midiaId}/tipo")
    public MidiaResponse alterarTipo(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                     @PathVariable Long midiaId, @RequestBody TipoMidiaRequest req) {
        return midiaService.alterarTipo(gestor, id, midiaId, req.tipo());
    }

    // ------------------------------------------------------ preco e publicacao

    @PutMapping("/{id}/preco")
    public AnuncioGestorResponse definirPreco(@AuthenticationPrincipal Usuario gestor,
                                              @PathVariable Long id, @RequestBody PrecoRequest req) {
        return anuncioService.definirPreco(gestor, id, req.valor(), req.origem());
    }

    @PostMapping("/{id}/publicar")
    public AnuncioGestorResponse publicar(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                          @RequestBody AceiteRequest req, HttpServletRequest http) {
        return anuncioService.publicar(gestor, id, req.aceiteTermo(), http.getRemoteAddr());
    }

    // ------------------------------------------------- edicao de anuncio publicado

    /** Tira o anuncio do ar e abre o rascunho. O corpo carrega o "sim" do modal de aviso. */
    @PostMapping("/{id}/edicao/iniciar")
    public AnuncioGestorResponse iniciarEdicao(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                               @RequestBody ConfirmacaoRequest req) {
        return edicaoService.iniciar(gestor, id, req.confirmado());
    }

    @PutMapping("/{id}/edicao/rascunho")
    public AnuncioGestorResponse salvarRascunho(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                                @RequestBody AnuncioDados dados) {
        return edicaoService.salvarRascunho(gestor, id, dados);
    }

    @PostMapping("/{id}/edicao/confirmar")
    public AnuncioGestorResponse confirmarEdicao(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                                 @RequestBody AceiteRequest req, HttpServletRequest http) {
        return edicaoService.confirmar(gestor, id, req.aceiteTermo(), http.getRemoteAddr());
    }

    @PostMapping("/{id}/edicao/descartar")
    public AnuncioGestorResponse descartarEdicao(@AuthenticationPrincipal Usuario gestor, @PathVariable Long id,
                                                 @RequestBody ConfirmacaoRequest req) {
        return edicaoService.descartar(gestor, id, req.confirmado());
    }
}
