package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.enums.TipoImovel;

import java.math.BigDecimal;

/**
 * Imóvel como o catálogo precisa dele.
 *
 * <p>O logradouro vai separado do número porque o cartão exibe apenas o nome
 * da via. Como a entidade já guarda os dois em colunas distintas, não há o que
 * extrair de uma string: basta não enviar o número.
 */
public record ImovelResponse(
        Long id,
        String titulo,
        TipoImovel tipoImovel,
        String tipoImovelRotulo,
        String logradouro,
        String bairro,
        String cidade,
        Integer metragemQuadrada,
        Integer numeroQuartos,
        Integer numeroBanheiros,
        Integer vagasGaragem,
        Integer capacidadeHospedes,
        BigDecimal valorDiariaBase,
        boolean ativo) {

    public static ImovelResponse de(Imovel i) {
        var end = i.getEndereco();
        return new ImovelResponse(
                i.getId(),
                i.getTitulo(),
                i.getTipoImovel(),
                rotulo(i.getTipoImovel()),
                end == null ? null : end.getLogradouro(),
                end == null ? null : end.getBairro(),
                end == null ? null : end.getCidade(),
                i.getMetragemQuadrada(),
                i.getNumeroQuartos(),
                i.getNumeroBanheiros(),
                i.getVagasGaragem(),
                i.getCapacidadeHospedes(),
                i.getValorDiariaBase(),
                i.isAtivo());
    }

    /** Nome legível do tipo; o enum em caixa alta não serve para exibição. */
    private static String rotulo(TipoImovel tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case APARTAMENTO -> "Apartamento";
            case CASA -> "Casa";
            case KITNET -> "Kitnet";
            case POUSADA -> "Pousada";
            case CHALE -> "Chalé";
            case LOFT -> "Loft";
            case OUTRO -> "Outro";
        };
    }
}
