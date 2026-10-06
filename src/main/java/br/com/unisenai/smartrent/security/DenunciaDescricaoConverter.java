package br.com.unisenai.smartrent.security;

import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Cifra da coluna {@code smartchat_denuncias.descricao} (AAD = o nome da coluna). */
@Component
@Converter
public class DenunciaDescricaoConverter extends TextoCifradoConverter {

    public static final String CONTEXTO = "smartchat_denuncias.descricao";

    public DenunciaDescricaoConverter(ObjectProvider<CifraCampo> cifra) {
        super(cifra, CONTEXTO);
    }
}
