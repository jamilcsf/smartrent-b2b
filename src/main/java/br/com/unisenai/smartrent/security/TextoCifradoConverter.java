package br.com.unisenai.smartrent.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Logger log = LoggerFactory.getLogger(TextoCifradoConverter.class);

    /** Exibido no lugar de um valor que nao decifra (adulterado, de outra coluna ou chave errada); nunca o conteudo. */
    public static final String INDISPONIVEL = "[mensagem indisponível]";

    private final ObjectProvider<CifraCampo> cifra;
    private final String contexto;

    // Dois construtores e nenhum anotado: o Spring exigiria um construtor padrao e a aplicacao nao subiria.
    @Autowired
    public TextoCifradoConverter(ObjectProvider<CifraCampo> cifra) {
        this(cifra, "");
    }

    /** {@code contexto} ("tabela.coluna") e autenticado junto com o texto (AAD): o valor so decifra na coluna de origem. */
    protected TextoCifradoConverter(ObjectProvider<CifraCampo> cifra, String contexto) {
        this.cifra = cifra;
        this.contexto = contexto;
    }

    @Override
    public String convertToDatabaseColumn(String atributo) {
        return atributo == null ? null : cifra.getObject().cifrar(atributo, contexto);
    }

    /**
     * Um valor que nao decifra nao derruba a leitura da conversa inteira: aparece {@link #INDISPONIVEL} e o erro vai ao
     * log (sem conteudo). Se TODOS os valores falharem, a causa e a chave (SMARTCHAT_CRYPTO_KEY) trocada ou errada.
     */
    @Override
    public String convertToEntityAttribute(String coluna) {
        if (coluna == null) {
            return null;
        }
        try {
            return cifra.getObject().decifrar(coluna, contexto);
        } catch (FalhaDecifragemException e) {
            log.error("Valor cifrado ilegivel na coluna {} ({}); exibindo aviso no lugar. Se isto se repete em todas "
                    + "as linhas, confira SMARTCHAT_CRYPTO_KEY.", contexto.isEmpty() ? "(sem contexto)" : contexto, e.getMessage());
            return INDISPONIVEL;
        }
    }
}
