package br.com.unisenai.smartrent.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Cifra o texto ao gravar e decifra ao ler (AES-256-GCM). Aplicado por {@code @Convert} apenas nas colunas
 * do SmartChat; nenhuma query pode filtrar ou ordenar por elas, pois o banco so ve o texto cifrado.
 *
 * <p>A cifra e resolvida sob demanda: o Hibernate instancia o conversor ao montar o mapeamento, e testes de
 * repositorio que nao tocam em texto de chat nao precisam registrar a cifra.
 */
@Component
@Converter
public class TextoCifradoConverter implements AttributeConverter<String, String> {

    private final ObjectProvider<CifraCampo> cifra;

    public TextoCifradoConverter(ObjectProvider<CifraCampo> cifra) {
        this.cifra = cifra;
    }

    @Override
    public String convertToDatabaseColumn(String atributo) {
        return atributo == null ? null : cifra.getObject().cifrar(atributo);
    }

    @Override
    public String convertToEntityAttribute(String coluna) {
        return coluna == null ? null : cifra.getObject().decifrar(coluna);
    }
}
