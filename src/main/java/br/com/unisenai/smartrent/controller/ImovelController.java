package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ImovelResponse;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/imoveis")
@CrossOrigin(origins = "*")
public class ImovelController {

    private final ImovelRepository imovelRepository;

    public ImovelController(ImovelRepository imovelRepository) {
        this.imovelRepository = imovelRepository;
    }

    /**
     * Catálogo público. {@code apenasAtivos} vem ligado por padrão: a vitrine
     * não deve anunciar imóvel inativo.
     */
    @GetMapping
    public List<ImovelResponse> listar(
            @RequestParam(defaultValue = "true") boolean apenasAtivos) {
        var imoveis = apenasAtivos ? imovelRepository.findByAtivoTrue() : imovelRepository.findAll();
        return imoveis.stream().map(ImovelResponse::de).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImovelResponse> buscarPorId(@PathVariable Long id) {
        return imovelRepository.findById(id)
                .map(ImovelResponse::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
