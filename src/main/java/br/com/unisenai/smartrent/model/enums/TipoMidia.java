package br.com.unisenai.smartrent.model.enums;

/** Natureza do arquivo anexado ao anuncio. Foto comum e 360 dividem o limite de imagens. */
public enum TipoMidia {
    FOTO,
    FOTO_360,
    VIDEO;

    public boolean imagem() {
        return this != VIDEO;
    }
}
