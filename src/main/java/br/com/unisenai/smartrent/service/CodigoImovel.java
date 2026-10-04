package br.com.unisenai.smartrent.service;

/**
 * Codigo legivel do imovel, usado nos seletores e filtros do gestor
 * (calendario, SmartChat). Derivado do identificador: IMV-000123.
 */
public final class CodigoImovel {

    private CodigoImovel() {
    }

    public static String de(Long id) {
        return id == null ? null : String.format("IMV-%06d", id);
    }
}
