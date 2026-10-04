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
        Integer duracaoSegundos,
        br.com.unisenai.smartrent.model.enums.StatusVideo statusProcessamento,
        String motivoFalha,
        String posterUrl,
        String hlsUrl,
        Long bytesRecebidos,
        Long tamanhoTotal) {

    public static MidiaResponse de(ImovelMidia m) {
        return de(m, m.getOrdem(), m.isCapa());
    }

    /** Variante com ordem e capa efetivas: durante uma edicao elas vem do rascunho, nao da linha. */
    public static MidiaResponse de(ImovelMidia m, int ordem, boolean capa) {
        String base = "/api/midias/" + m.getChave();
        return new MidiaResponse(
                m.getId(),
                m.getTipo(),
                m.getEstado(),
                base,
                m.getMiniatura() == null ? null : base + "/miniatura",
                ordem,
                capa,
                m.getLargura(),
                m.getAltura(),
                m.getDuracaoSegundos(),
                m.getStatusProcessamento(),
                m.getMotivoFalha(),
                m.getPoster() == null ? null : base + "/poster",
                m.getHlsMestre() == null ? null : base + "/hls/master.m3u8",
                m.getBytesRecebidos(),
                m.getTamanhoTotal());
    }
}
