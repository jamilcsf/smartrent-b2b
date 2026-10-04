package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;

/**
 * Unico caminho para trocar o status de um anuncio: confere a tabela de
 * transicoes de {@link StatusAnuncio} e recusa o que ela nao permite.
 */
final class MaquinaDeEstados {

    private MaquinaDeEstados() {
    }

    static void mover(Imovel imovel, StatusAnuncio destino) {
        StatusAnuncio origem = imovel.getStatus();
        if (!origem.podeIr(destino)) {
            throw new TransicaoInvalidaException(
                    "Transição não permitida: " + origem + " → " + destino + ".");
        }
        imovel.setStatus(destino);
    }
}
