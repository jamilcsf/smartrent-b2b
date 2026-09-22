package br.com.unisenai.smartrent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cliente da Groq API (Llama 3.3) para sugestão de preço.
 *
 * <p><strong>A integração ainda não está implementada.</strong> Enquanto não
 * estiver, este cliente falha explicitamente, e é a regra de contingência do
 * {@link SugestaoPrecoService} que produz o valor — comportamento previsto no
 * RF09 e registrado em cada sugestão pelo campo origem do cálculo.
 *
 * <p>Falhar é deliberado: devolver um valor fixo faria o sistema anunciar como
 * sugestão de IA um número que nenhuma IA calculou, e a origem gravada no banco
 * ficaria mentindo. Com a falha, a sugestão sai honestamente marcada como
 * contingência.
 */
@Component
public class GroqApiClient {

    private final String apiKey;

    public GroqApiClient(@Value("${smartrent.groq.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public BigDecimal buscarSugestaoIA(Long imovelId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Chave da Groq API não configurada (GROQ_API_KEY).");
        }
        // TODO: chamar a Groq API com RestClient, conforme RNF01, e interpretar
        // a resposta do Llama 3.3 em valor sugerido e justificativa.
        throw new UnsupportedOperationException(
                "Integração com a Groq API ainda não implementada.");
    }
}
