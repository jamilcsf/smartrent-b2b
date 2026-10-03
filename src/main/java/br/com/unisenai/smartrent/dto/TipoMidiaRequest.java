package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.TipoMidia;

/** Novo tipo de uma imagem: FOTO (comum) ou FOTO_360. */
public record TipoMidiaRequest(TipoMidia tipo) {
}
