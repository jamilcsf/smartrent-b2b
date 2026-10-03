package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.SugestaoPrecoLoteRequest;
import br.com.unisenai.smartrent.dto.SugestaoPrecoLoteResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.PrecificacaoLoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Painel do gestor (pre-publicacao): sugestao de preco por IA em lote. So sugere; nao salva. */
@RestController
@RequestMapping("/api/gestor/precificacao")
public class PrecificacaoGestorController {

    private final PrecificacaoLoteService loteService;

    public PrecificacaoGestorController(PrecificacaoLoteService loteService) {
        this.loteService = loteService;
    }

    @PostMapping("/sugestoes")
    public SugestaoPrecoLoteResponse sugerir(@AuthenticationPrincipal Usuario gestor,
                                             @RequestBody SugestaoPrecoLoteRequest req) {
        return loteService.sugerir(gestor, req.imovelIds());
    }
}
