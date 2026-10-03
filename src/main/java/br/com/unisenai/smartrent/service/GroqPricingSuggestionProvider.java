package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sugestao de preco via Groq (API compativel com a da OpenAI), usando o
 * {@link RestClient} sincrono do Spring. Provedor, modelo, chave e prazo vem do
 * ambiente.
 *
 * <p><b>O que vai para a IA</b>: tipo do imovel, bairro, cidade, UF, area,
 * quartos, banheiros, vagas, capacidade, comodidades e a data de hoje (para
 * sazonalidade). <b>O que nao vai</b>: titulo e descricao (texto livre),
 * logradouro, numero, CEP, coordenadas, links e qualquer dado do gestor.
 *
 * <p>Qualquer falha (sem chave, rede, HTTP, timeout, resposta que nao e o JSON
 * esperado, valor fora da faixa) vira {@link PricingIndisponivelException}
 * com mensagem clara; o fallback e o gestor definir o valor manualmente.
 */
@Service
public class GroqPricingSuggestionProvider implements PricingSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(GroqPricingSuggestionProvider.class);
    private static final BigDecimal MIN = new BigDecimal("1.00");
    private static final BigDecimal MAX = new BigDecimal("100000.00");

    private static final String SISTEMA = "Você é um especialista em precificação de aluguel por temporada "
            + "no Brasil, com foco na Grande Florianópolis. Sugira o valor da diária em reais (BRL) para o "
            + "imóvel descrito, considerando características, localização, área e a época do ano. "
            + "Responda SOMENTE com um objeto JSON no formato "
            + "{\"valor\": <número, diária em reais>, \"justificativa\": \"<até 300 caracteres, em português>\"}.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelo;
    private final Clock clock;

    @Autowired
    public GroqPricingSuggestionProvider(RestClient.Builder builder,
                                         ObjectMapper objectMapper,
                                         Clock clock,
                                         @Value("${smartrent.groq.api-key:}") String apiKey,
                                         @Value("${smartrent.groq.model:llama-3.3-70b-versatile}") String modelo,
                                         @Value("${smartrent.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                                         @Value("${smartrent.groq.timeout-ms:15000}") long timeoutMs) {
        this(comTimeout(builder, baseUrl, timeoutMs), objectMapper, clock, apiKey, modelo);
    }

    /** Construtor para testes: recebe o cliente HTTP ja montado. */
    GroqPricingSuggestionProvider(RestClient restClient, ObjectMapper objectMapper, Clock clock,
                                  String apiKey, String modelo) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.apiKey = apiKey;
        this.modelo = modelo;
    }

    private static RestClient comTimeout(RestClient.Builder builder, String baseUrl, long timeoutMs) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        return builder.baseUrl(baseUrl).requestFactory(fabrica).build();
    }

    @Override
    public Sugestao sugerir(Imovel imovel) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new PricingIndisponivelException(
                    "A sugestão por IA não está configurada neste ambiente. Defina o valor manualmente.");
        }

        String resposta;
        try {
            resposta = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", modelo,
                            "temperature", 0.2,
                            "response_format", Map.of("type", "json_object"),
                            "messages", List.of(
                                    Map.of("role", "system", "content", SISTEMA),
                                    Map.of("role", "user", "content", descrever(imovel)))))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("Falha ao consultar a IA de precificação: {}", e.getMessage());
            throw new PricingIndisponivelException(
                    "A IA de precificação não respondeu (indisponível ou demorou demais). "
                            + "Tente novamente ou defina o valor manualmente.", e);
        }
        return interpretar(resposta);
    }

    /** Somente atributos do imovel e a data: nenhum dado pessoal nem texto livre. */
    String descrever(Imovel i) {
        Endereco e = i.getEndereco();
        StringBuilder sb = new StringBuilder();
        sb.append("Data de hoje: ").append(LocalDate.now(clock)).append('\n');
        sb.append("Tipo: ").append(i.getTipoImovel()).append('\n');
        if (e != null) {
            sb.append("Bairro: ").append(e.getBairro()).append('\n');
            sb.append("Cidade/UF: ").append(e.getCidade()).append('/').append(e.getEstado()).append('\n');
        }
        if (i.getMetragemQuadrada() != null) {
            sb.append("Área: ").append(i.getMetragemQuadrada()).append(" m²\n");
        }
        sb.append("Quartos: ").append(i.getNumeroQuartos()).append('\n');
        sb.append("Banheiros: ").append(i.getNumeroBanheiros()).append('\n');
        if (i.getVagasGaragem() != null) {
            sb.append("Vagas de garagem: ").append(i.getVagasGaragem()).append('\n');
        }
        sb.append("Capacidade: ").append(i.getCapacidadeHospedes()).append(" hóspedes\n");
        if (i.getComodidades() != null && !i.getComodidades().isEmpty()) {
            sb.append("Comodidades: ").append(String.join(", ", i.getComodidades())).append('\n');
        }
        return sb.toString();
    }

    private Sugestao interpretar(String corpo) {
        try {
            JsonNode raiz = objectMapper.readTree(corpo);
            String conteudo = raiz.path("choices").path(0).path("message").path("content").asText("");
            JsonNode json = objectMapper.readTree(conteudo);
            BigDecimal valor = lerValor(json.path("valor"));
            if (valor == null || valor.compareTo(MIN) < 0 || valor.compareTo(MAX) > 0) {
                throw new PricingIndisponivelException(
                        "A IA devolveu um valor inválido. Defina o valor manualmente.");
            }
            String justificativa = json.path("justificativa").asText("");
            if (justificativa.length() > 300) {
                justificativa = justificativa.substring(0, 300);
            }
            return new Sugestao(valor.setScale(2, java.math.RoundingMode.HALF_UP),
                    Sanitizador.linha(justificativa), modelo);
        } catch (PricingIndisponivelException e) {
            throw e;
        } catch (Exception e) {
            throw new PricingIndisponivelException(
                    "A resposta da IA não pôde ser interpretada. Defina o valor manualmente.", e);
        }
    }

    private static BigDecimal lerValor(JsonNode no) {
        if (no.isNumber()) {
            return no.decimalValue();
        }
        if (no.isTextual()) {
            String t = no.asText().replace("R$", "").trim();
            if (t.contains(",")) {
                t = t.replace(".", "").replace(',', '.');
            }
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
