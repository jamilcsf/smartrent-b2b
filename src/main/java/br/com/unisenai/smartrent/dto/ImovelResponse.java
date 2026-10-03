package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.TipoImovel;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Imóvel como o catálogo precisa dele.
 *
 * <p>O logradouro vai separado do número porque o cartão exibe apenas o nome
 * da via. Como a entidade já guarda os dois em colunas distintas, não há o que
 * extrair de uma string: basta não enviar o número.
 *
 * <p>{@code whatsappLink} só é preenchido para quem está logado e, nulo, nem
 * sequer entra no JSON: o dado não sai do servidor para visitantes anônimos.
 */
public record ImovelResponse(
        Long id,
        String titulo,
        String descricao,
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
        List<String> comodidades,
        boolean ativo,
        String capaUrl,
        List<MidiaResponse> midias,
        @JsonInclude(JsonInclude.Include.NON_NULL) String whatsappLink) {

    /**
     * @param midias           midias do imóvel; só as ATIVAS são expostas
     * @param incluirWhatsapp  verdadeiro apenas para requisições autenticadas
     * @param comMidias        falso no catálogo: lá só a capa interessa
     */
    public static ImovelResponse de(Imovel i, List<ImovelMidia> midias,
                                    boolean incluirWhatsapp, boolean comMidias) {
        var ativas = midias.stream()
                .filter(m -> m.getEstado() == EstadoMidia.ATIVA)
                .toList();
        var capa = ativas.stream().filter(ImovelMidia::isCapa).findFirst()
                .or(() -> ativas.stream().filter(m -> m.getTipo().imagem()).findFirst())
                .map(MidiaResponse::de)
                .orElse(null);
        var end = i.getEndereco();
        return new ImovelResponse(
                i.getId(),
                i.getTitulo(),
                i.getDescricao(),
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
                i.getComodidades() == null ? List.of() : List.copyOf(i.getComodidades()),
                i.isAtivo(),
                capa == null ? null : (capa.miniaturaUrl() != null ? capa.miniaturaUrl() : capa.url()),
                comMidias ? ativas.stream().map(MidiaResponse::de).toList() : List.of(),
                incluirWhatsapp ? i.getWhatsappLink() : null);
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
