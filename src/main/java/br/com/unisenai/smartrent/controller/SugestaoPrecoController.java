package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.SugestaoPrecoResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ImovelAcesso;
import br.com.unisenai.smartrent.service.SugestaoPrecoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sugestao de preco de UM imovel. Grava a sugestao e consulta a IA (custo), por isso so o gestor dono do
 * imovel (ou admin) chega aqui: a regra de papel esta no {@code SecurityConfig} e a de posse no
 * {@link ImovelAcesso}. Sem {@code @CrossOrigin}: o front e servido pela propria aplicacao.
 */
@RestController
@RequestMapping("/api/precificacao")
public class SugestaoPrecoController {

    private static final BigDecimal VALOR_BASE_MAXIMO = new BigDecimal("1000000");

    private final SugestaoPrecoService sugestaoPrecoService;
    private final ImovelAcesso acesso;

    public SugestaoPrecoController(SugestaoPrecoService sugestaoPrecoService, ImovelAcesso acesso) {
        this.sugestaoPrecoService = sugestaoPrecoService;
        this.acesso = acesso;
    }

    @GetMapping("/sugerir")
    public SugestaoPrecoResponse obterSugestao(
            @AuthenticationPrincipal Usuario gestor,
            @RequestParam Long imovelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataRef,
            @RequestParam BigDecimal valorBase) {
        if (valorBase == null || valorBase.signum() <= 0 || valorBase.compareTo(VALOR_BASE_MAXIMO) > 0) {
            throw new IllegalArgumentException("Informe um valor base maior que zero.");
        }
        Imovel imovel = acesso.doGestor(gestor, imovelId);
        return SugestaoPrecoResponse.de(sugestaoPrecoService.gerarSugestaoPreco(imovel, dataRef, valorBase));
    }
}
