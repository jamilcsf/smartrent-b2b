package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.StatusAnuncio;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Anuncio como o gestor o ve no dashboard e no formulario.
 *
 * <p>{@code dados} e sempre o anuncio vivo. Em {@code EM_EDICAO},
 * {@code rascunho} traz o que o gestor esta editando; o formulario parte dele.
 * {@code agora} e o relogio do servidor: as contagens regressivas do front
 * partem dele, nao do relogio do navegador. Os prazos configurados (24h/2h)
 * tambem vao na resposta, para os avisos da tela nao repetirem numeros fixos.
 */
public record AnuncioGestorResponse(
        Long id,
        StatusAnuncio status,
        boolean ativo,
        boolean noCatalogo,
        AnuncioDados dados,
        AnuncioDados rascunho,
        List<MidiaResponse> midias,
        int totalImagens,
        int totalVideos,
        int limiteImagens,
        int limiteVideos,
        LocalDateTime agora,
        LocalDateTime dataCadastro,
        LocalDateTime precoPrimeiraConfirmacaoEm,
        LocalDateTime prontoParaPublicarEm,
        boolean podePublicar,
        LocalDateTime publicadoEm,
        LocalDateTime edicaoIniciadaEm,
        StatusAnuncio edicaoEstadoOrigem,
        LocalDateTime edicaoConfirmadaEm,
        LocalDateTime republicarEm,
        LocalDateTime republicarOriginalEm,
        long janelaPrePublicacaoHoras,
        long republicacaoHoras) {
}
