package br.com.unisenai.smartrent.security;

import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Cifra da coluna {@code notificacoes.mensagem} (AAD = o nome da coluna). */
@Component
@Converter
public class NotificacaoMensagemConverter extends TextoCifradoConverter {

    public static final String CONTEXTO = "notificacoes.mensagem";

    public NotificacaoMensagemConverter(ObjectProvider<CifraCampo> cifra) {
        super(cifra, CONTEXTO);
    }
}
