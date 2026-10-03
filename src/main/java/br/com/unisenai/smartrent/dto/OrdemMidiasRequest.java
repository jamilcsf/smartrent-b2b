package br.com.unisenai.smartrent.dto;

import java.util.List;

/** Nova ordem das midias: os ids de todas as midias do anuncio, na ordem desejada. */
public record OrdemMidiasRequest(List<Long> ids) {
}
