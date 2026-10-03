package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.TipoMidia;

/**
 * Midia como a API a expoe. A URL usa a chave aleatoria, nunca o id
 * sequencial, e serve o arquivo sem exigir token: e o que permite usar a
 * propria URL em {@code <img>} e {@code <video>}.
 */
public record MidiaResponse(
        Long id,
        TipoMidia tipo,
        EstadoMidia estado,
        String url,
        String miniaturaUrl,
        int ordem,
        boolean capa,
        Integer largura,
        Integer altura,
        Integer duracaoSegundos) {

    public static MidiaResponse de(ImovelMidia m) {
        String base = "/api/midias/" + m.getChave();
        return new MidiaResponse(
                m.getId(),
                m.getTipo(),
                m.getEstado(),
                base,
                m.getMiniatura() == null ? null : base + "/miniatura",
                m.getOrdem(),
                m.isCapa(),
                m.getLargura(),
                m.getAltura(),
                m.getDuracaoSegundos());
    }
}
