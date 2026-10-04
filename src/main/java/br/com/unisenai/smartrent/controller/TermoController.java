package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.TermoUsoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Texto do termo de uso, publico para poder ser lido antes do aceite. */
@RestController
@RequestMapping("/api/termos")
public class TermoController {

    @GetMapping("/atual")
    public TermoUsoResponse atual() {
        return TermoUsoResponse.atual();
    }
}
