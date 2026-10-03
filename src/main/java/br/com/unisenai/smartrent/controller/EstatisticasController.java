package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.EstatisticasResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.EstatisticasService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard do gestor: so estatisticas, so dos imoveis de quem esta autenticado. */
@RestController
@RequestMapping("/api/gestor/estatisticas")
public class EstatisticasController {

    private final EstatisticasService service;

    public EstatisticasController(EstatisticasService service) {
        this.service = service;
    }

    @GetMapping
    public EstatisticasResponse estatisticas(@AuthenticationPrincipal Usuario gestor,
                                             @RequestParam(required = false) Integer meses) {
        return service.calcular(gestor, meses);
    }
}
