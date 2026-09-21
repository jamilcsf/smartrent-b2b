package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.repository.SugestaoPrecoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class SugestaoPrecoService {

    private final GroqApiClient groqApiClient;
    private final SugestaoPrecoRepository sugestaoPrecoRepository;

    public SugestaoPrecoService(GroqApiClient groqApiClient, SugestaoPrecoRepository sugestaoPrecoRepository) {
        this.groqApiClient = groqApiClient;
        this.sugestaoPrecoRepository = sugestaoPrecoRepository;
    }

    public SugestaoPreco gerarSugestaoPreco(Long imovelId, LocalDate dataRef, BigDecimal valorBase) {
        SugestaoPreco sugestao = new SugestaoPreco();
        sugestao.setImovelId(imovelId);
        sugestao.setDataReferencia(dataRef);

        try {
            BigDecimal valorIA = groqApiClient.buscarSugestaoIA(imovelId);
            sugestao.setValorSugerido(valorIA);
            sugestao.setOrigemCalculo("IA_GROQ");
        } catch (Exception e) {
            // Regra de Contingência: Acionada quando a IA falha (RF09)
            BigDecimal valorContingencia = valorBase.multiply(new BigDecimal("1.10"));
            sugestao.setValorSugerido(valorContingencia);
            sugestao.setOrigemCalculo("CONTINGENCIA");
        }

        return sugestaoPrecoRepository.save(sugestao);
    }
}