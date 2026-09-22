package br.com.unisenai.smartrent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Rotas legiveis para as paginas estaticas.
 *
 * <p>O front nao usa roteador de SPA, entao o caminho /imoveis/{id} e
 * resolvido aqui: o Spring encaminha internamente para o arquivo estatico e
 * a pagina le o identificador do proprio caminho. O navegador mantem a URL
 * limpa, e um link compartilhado continua funcionando.
 *
 * <p>Nao conflita com /api/imoveis/{id}: os prefixos sao distintos.
 */
@Controller
public class PaginasController {

    @GetMapping("/imoveis/{id}")
    public String detalheDoImovel(@PathVariable Long id) {
        return "forward:/imovel.html";
    }
}
