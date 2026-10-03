package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validacao no servidor dos campos comerciais do anuncio (o front valida so por conveniencia). */
class AnuncioDadosValidacaoTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static AnuncioDados com(Integer capacidade, Integer minimo, BigDecimal taxa) {
        return new AnuncioDados("Apto", "d", TipoImovel.APARTAMENTO, 70, 2, 1, 1, capacidade, List.of(),
                "88054-000", "Rua A", "1", null, "Centro", "Florianopolis", "SC", minimo, taxa, null);
    }

    private static Set<String> campos(AnuncioDados d) {
        return VALIDADOR.validate(d).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("CT204 - Valores validos passam; ausentes assumem minimo 1 e taxa 0 (rascunhos antigos)")
    void validos() {
        assertTrue(campos(com(4, 1, BigDecimal.ZERO)).isEmpty());
        assertTrue(campos(com(4, 3, new BigDecimal("120.50"))).isEmpty());
        assertTrue(campos(com(4, null, null)).isEmpty());
        var imovel = new br.com.unisenai.smartrent.model.Imovel();
        AnuncioCampos.aplicar(imovel, com(4, null, null));
        assertEquals(1, imovel.getMinimoDiarias());
        assertEquals(0, BigDecimal.ZERO.compareTo(imovel.getTaxaLimpeza()));
    }

    @Test
    @DisplayName("CT205 - Minimo de diarias fora da faixa, taxa negativa ou com mais de 2 casas e limite de hospedes invalido sao rejeitados")
    void invalidos() {
        assertTrue(campos(com(4, 0, BigDecimal.ZERO)).contains("minimoDiarias"));
        assertTrue(campos(com(4, -2, BigDecimal.ZERO)).contains("minimoDiarias"));
        assertTrue(campos(com(4, 366, BigDecimal.ZERO)).contains("minimoDiarias"));
        assertTrue(campos(com(4, 1, new BigDecimal("-0.01"))).contains("taxaLimpeza"));
        assertTrue(campos(com(4, 1, new BigDecimal("10.123"))).contains("taxaLimpeza"));
        assertTrue(campos(com(0, 1, BigDecimal.ZERO)).contains("capacidadeHospedes"));
        assertTrue(campos(com(51, 1, BigDecimal.ZERO)).contains("capacidadeHospedes"));
        assertTrue(campos(com(null, 1, BigDecimal.ZERO)).contains("capacidadeHospedes"));
    }
}
