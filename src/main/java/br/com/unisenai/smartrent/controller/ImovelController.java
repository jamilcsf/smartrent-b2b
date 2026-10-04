package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ImovelResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catalogo publico. So devolve imoveis visiveis (ver
 * {@link ImovelRepository#VISIVEL}): em pre-publicacao, em edicao ou com a
 * republicacao ainda por vir, o imovel nao existe para esta API.
 */
@RestController
@RequestMapping("/api/imoveis")
@CrossOrigin(origins = "*")
public class ImovelController {

    private final ImovelRepository imovelRepository;
    private final ImovelMidiaRepository midiaRepository;
    private final Clock clock;

    public ImovelController(ImovelRepository imovelRepository,
                            ImovelMidiaRepository midiaRepository,
                            Clock clock) {
        this.imovelRepository = imovelRepository;
        this.midiaRepository = midiaRepository;
        this.clock = clock;
    }

    /**
     * O parametro {@code apenasAtivos} sobrevive por compatibilidade com o
     * front antigo, mas deixou de ter efeito: a vitrine publica nunca lista
     * anuncio fora do ar.
     */
    @GetMapping
    public List<ImovelResponse> listar(@RequestParam(defaultValue = "true") boolean apenasAtivos) {
        var imoveis = imovelRepository.findVisiveis(LocalDateTime.now(clock));
        if (imoveis.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ImovelMidia>> midias = midiaRepository
                .findByImovelIdInOrderByOrdemAscIdAsc(imoveis.stream().map(Imovel::getId).toList())
                .stream().collect(Collectors.groupingBy(m -> m.getImovel().getId()));
        return imoveis.stream()
                .map(i -> ImovelResponse.de(i, midias.getOrDefault(i.getId(), List.of()), false))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImovelResponse> buscarPorId(@PathVariable Long id) {
        return imovelRepository.findVisivelPorId(id, LocalDateTime.now(clock))
                .map(i -> ImovelResponse.de(i,
                        midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(i.getId()), true))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
