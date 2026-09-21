package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.model.enums.OrigemCalculoPreco;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Sugestao de preco devolvida pela API, com a origem do calculo exposta. */
public record SugestaoPrecoResponse(
        Long id,
        Long imovelId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
        BigDecimal valorBase,
        BigDecimal valorSugerido,
        BigDecimal percentualAjuste,
        BigDecimal fatorSazonalidade,
        BigDecimal fatorMicrogeografia,
        String justificativaIa,
        String modeloIaUtilizado,
        OrigemCalculoPreco origemCalculo,
        LocalDateTime dataGeracao) {

    public static SugestaoPrecoResponse de(SugestaoPreco s) {
        return new SugestaoPrecoResponse(
                s.getId(),
                s.getImovel() == null ? null : s.getImovel().getId(),
                s.getDataReferencia(),
                s.getValorBase(),
                s.getValorSugerido(),
                s.getPercentualAjuste(),
                s.getFatorSazonalidade(),
                s.getFatorMicrogeografia(),
                s.getJustificativaIa(),
                s.getModeloIaUtilizado(),
                s.getOrigemCalculo(),
                s.getDataGeracao());
    }
}
