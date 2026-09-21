package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.repository.SugestaoPrecoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

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
  @DisplayName("CT09 - Deve acionar calculo de contingencia quando a chamada a IA retorna erro")
  void deveAcionarContingenciaQuandoIaFalhar() {
    Long imovelId = 1L;
    LocalDate dataRef = LocalDate.now();
    BigDecimal valorBase = new BigDecimal("200.00");

    when(groqApiClient.buscarSugestaoIA(any())).thenThrow(new RestClientException("IA Indisponivel"));
    when(sugestaoPrecoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    SugestaoPreco resultado = sugestaoPrecoService.gerarSugestaoPreco(imovelId, dataRef, valorBase);

    assertNotNull(resultado);
    assertEquals("CONTINGENCIA", resultado.getOrigemCalculo());
    assertTrue(resultado.getValorSugerido().compareTo(valorBase) > 0);
    verify(sugestaoPrecoRepository, times(1)).save(any());
  }
}