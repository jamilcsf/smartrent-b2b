package br.com.unisenai.smartrent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Corpo do cadastro: os campos do anuncio mais o aceite do termo de uso. */
public record CriarAnuncioRequest(
        @NotNull(message = "Informe os dados do imóvel.") @Valid AnuncioDados dados,
        Boolean aceiteTermo) {
}
