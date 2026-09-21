package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.service.SugestaoPrecoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/precificacao")
@CrossOrigin(origins = "*")
public class SugestaoPrecoController {

    private final SugestaoPrecoService sugestaoPrecoService;

    public SugestaoPrecoController(SugestaoPrecoService sugestaoPrecoService) {
        this.sugestaoPrecoService = sugestaoPrecoService;
    }

    @GetMapping("/sugerir")
    public ResponseEntity<SugestaoPreco> obterSugestao(
            @RequestParam Long imovelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataRef,
            @RequestParam BigDecimal valorBase) {
        
        SugestaoPreco sugestao = sugestaoPrecoService.gerarSugestaoPreco(imovelId, dataRef, valorBase);
        return ResponseEntity.ok(sugestao);
    }
}