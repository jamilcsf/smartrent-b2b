package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.model.enums.OrigemCalculoPreco;
import br.com.unisenai.smartrent.repository.SugestaoPrecoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class SugestaoPrecoService {

    /** Acrescimo aplicado quando a IA nao responde (RF09). */
    private static final BigDecimal FATOR_CONTINGENCIA = new BigDecimal("1.10");

    private final GroqApiClient groqApiClient;
    private final SugestaoPrecoRepository sugestaoPrecoRepository;

    public SugestaoPrecoService(GroqApiClient groqApiClient,
                                SugestaoPrecoRepository sugestaoPrecoRepository) {
        this.groqApiClient = groqApiClient;
        this.sugestaoPrecoRepository = sugestaoPrecoRepository;
    }

    public SugestaoPreco gerarSugestaoPreco(Imovel imovel, LocalDate dataRef, BigDecimal valorBase) {
        SugestaoPreco sugestao = new SugestaoPreco();
        sugestao.setImovel(imovel);
        sugestao.setDataReferencia(dataRef);
        sugestao.setValorBase(valorBase);

        try {
            BigDecimal valorIA = groqApiClient.buscarSugestaoIA(imovel.getId());
            sugestao.setValorSugerido(valorIA);
            sugestao.setOrigemCalculo(OrigemCalculoPreco.IA_GENERATIVA);
        } catch (Exception e) {
            // Regra de contingencia: acionada quando a IA falha (RF09).
            sugestao.setValorSugerido(valorBase.multiply(FATOR_CONTINGENCIA));
            sugestao.setOrigemCalculo(OrigemCalculoPreco.FALLBACK_REGRA_NEGOCIO);
        }

        sugestao.setPercentualAjuste(calcularPercentualAjuste(valorBase, sugestao.getValorSugerido()));
        return sugestaoPrecoRepository.save(sugestao);
    }

    /** Variacao percentual do valor sugerido em relacao a base, para auditoria. */
    private BigDecimal calcularPercentualAjuste(BigDecimal base, BigDecimal sugerido) {
        if (base == null || sugerido == null || base.signum() == 0) {
            return null;
        }
        return sugerido.subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }
}
