package br.com.unisenai.smartrent.service.erro;

import java.util.List;
import java.util.Map;

/**
 * Conflito (HTTP 409) que carrega o que conflitou, para a interface mostrar
 * quais datas ou reservas impedem a operacao e oferecer a alternativa.
 */
public class ConflitoComDetalhesException extends RuntimeException {

    private final String codigo;
    private final transient List<Map<String, Object>> detalhes;

    public ConflitoComDetalhesException(String codigo, String mensagem, List<Map<String, Object>> detalhes) {
        super(mensagem);
        this.codigo = codigo;
        this.detalhes = detalhes;
    }

    public String codigo() {
        return codigo;
    }

    public List<Map<String, Object>> detalhes() {
        return detalhes;
    }
}
