package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.SugestaoPrecoResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.SugestaoPrecoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/precificacao")
@CrossOrigin(origins = "*")
public class SugestaoPrecoController {

    private final SugestaoPrecoService sugestaoPrecoService;
    private final ImovelRepository imovelRepository;

    public SugestaoPrecoController(SugestaoPrecoService sugestaoPrecoService,
                                   ImovelRepository imovelRepository) {
        this.sugestaoPrecoService = sugestaoPrecoService;
        this.imovelRepository = imovelRepository;
    }

    @GetMapping("/sugerir")
    public ResponseEntity<?> obterSugestao(
            @RequestParam Long imovelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataRef,
            @RequestParam BigDecimal valorBase) {

        Optional<Imovel> imovel = imovelRepository.findById(imovelId);
        if (imovel.isEmpty()) {
            return ResponseEntity.badRequest().body("Imovel nao encontrado: " + imovelId);
        }

        return ResponseEntity.ok(SugestaoPrecoResponse.de(
                sugestaoPrecoService.gerarSugestaoPreco(imovel.get(), dataRef, valorBase)));
    }
}
