package br.com.unisenai.smartrent.service;

/**
 * Moderacao de conteudo do SmartChat. O texto e tratado por
 * {@link MessageFilterService}. O pipeline de IMAGENS fica preparado aqui, mas
 * ainda nao esta implementado porque esta etapa nao aceita anexos: quando forem
 * liberados, imagens com nudez deverao ser borradas ou bloqueadas por uma
 * implementacao deste metodo, sem mexer no fluxo do chat.
 */
public interface ContentModerationService {

    MessageFilterService.Resultado moderarTexto(String texto);

    /** Ainda nao implementado (sem anexos de imagem nesta etapa): lanca {@link UnsupportedOperationException}. */
    default ResultadoImagem moderarImagem(byte[] imagem, String tipoMime) {
        throw new UnsupportedOperationException("Anexos de imagem ainda não são aceitos no SmartChat; a moderação de imagens será ligada com eles.");
    }

    /** Decisao para uma imagem: permitida, borrada ou bloqueada. */
    record ResultadoImagem(boolean permitida, boolean borrar, String motivo) {
    }
}
