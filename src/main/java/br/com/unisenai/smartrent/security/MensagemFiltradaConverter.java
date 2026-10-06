package br.com.unisenai.smartrent.security;

import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Cifra da coluna {@code smartchat_mensagens.texto_filtrado} (AAD = o nome da coluna). */
@Component
@Converter
public class MensagemFiltradaConverter extends TextoCifradoConverter {

    public static final String CONTEXTO = "smartchat_mensagens.texto_filtrado";

    public MensagemFiltradaConverter(ObjectProvider<CifraCampo> cifra) {
        super(cifra, CONTEXTO);
    }
}
