package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.model.enums.OrigemCalculoPreco;
import br.com.unisenai.smartrent.repository.SugestaoPrecoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SugestaoPrecoServiceTest {

    @Mock
    private GroqApiClient groqApiClient;

    @Mock
    private SugestaoPrecoRepository sugestaoPrecoRepository;

    @InjectMocks
    private SugestaoPrecoService sugestaoPrecoService;

    @Test
    @DisplayName("CT05 - Deve acionar a contingencia quando a IA falhar")
    void deveAcionarContingenciaQuandoIaFalhar() {
        Imovel imovel = new Imovel();
        imovel.setId(1L);
        BigDecimal valorBase = new BigDecimal("200.00");

        // RNF04: a chamada externa e simulada, o build nao depende de rede.
        when(groqApiClient.buscarSugestaoIA(1L)).thenThrow(new RuntimeException("IA indisponivel"));
        when(sugestaoPrecoRepository.save(any(SugestaoPreco.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SugestaoPreco sugestao = sugestaoPrecoService.gerarSugestaoPreco(
                imovel, LocalDate.now(), valorBase);

        assertEquals(OrigemCalculoPreco.FALLBACK_REGRA_NEGOCIO, sugestao.getOrigemCalculo());
        assertEquals(0, new BigDecimal("220.000").compareTo(sugestao.getValorSugerido()));
        assertEquals(0, new BigDecimal("10.00").compareTo(sugestao.getPercentualAjuste()));
    }
}
