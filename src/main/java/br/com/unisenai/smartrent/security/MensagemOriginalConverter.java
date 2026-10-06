package br.com.unisenai.smartrent.security;

import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Cifra da coluna {@code smartchat_mensagens.texto_original} (AAD = o nome da coluna). */
@Component
@Converter
public class MensagemOriginalConverter extends TextoCifradoConverter {

    public static final String CONTEXTO = "smartchat_mensagens.texto_original";

    public MensagemOriginalConverter(ObjectProvider<CifraCampo> cifra) {
        super(cifra, CONTEXTO);
    }
}
