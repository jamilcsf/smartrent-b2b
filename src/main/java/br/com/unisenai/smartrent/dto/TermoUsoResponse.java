package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.service.TermoUso;

/** Termo de uso vigente. */
public record TermoUsoResponse(String versao, String titulo, String texto) {

    public static TermoUsoResponse atual() {
        return new TermoUsoResponse(TermoUso.VERSAO, TermoUso.TITULO, TermoUso.TEXTO);
    }
}
