package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.service.TextosPoliticas;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Texto da politica de cancelamento, publico (visivel antes de reservar). O texto
 * e PROVISORIO e vem do recurso unico de textos pendentes de revisao juridica.
 */
@RestController
@RequestMapping("/api/politica-cancelamento")
public class PoliticaController {

    private final PoliticaCancelamentoProperties props;
    private final TextosPoliticas textos;

    public PoliticaController(PoliticaCancelamentoProperties props, TextosPoliticas textos) {
        this.props = props;
        this.textos = textos;
    }

    @GetMapping
    public Map<String, Object> politica() {
        int h = props.antecedenciaHoras();
        return Map.of(
                "versao", props.versao(),
                "provisoria", true,
                "resumo", textos.get("politica.resumo", h, props.checkinHora()),
                "texto", textos.get("politica.texto-completo", h, h, h));
    }
}
