package br.com.unisenai.smartrent.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway de teste: aprova qualquer cobranca, exceto o token "recusado", e
 * estorna tudo. E idempotente por chave, como um provedor real. Em producao,
 * troque por uma implementacao de {@link GatewayPagamento} do provedor escolhido.
 */
@Component
public class GatewayPagamentoSandbox implements GatewayPagamento {

    public static final String TOKEN_RECUSADO = "recusado";

    private final Map<String, Resultado> resultados = new ConcurrentHashMap<>();

    @Override
    public Resultado cobrar(String chave, BigDecimal valor, String moeda, String token) {
        return resultados.computeIfAbsent("cobranca:" + chave, k ->
                TOKEN_RECUSADO.equalsIgnoreCase(token == null ? "" : token.trim())
                        ? Resultado.recusado("Pagamento recusado pelo emissor (sandbox).")
                        : Resultado.ok("SBX-C-" + Math.abs(chave.hashCode())));
    }

    @Override
    public Resultado estornar(String chave, String refCobranca, BigDecimal valor) {
        return resultados.computeIfAbsent("estorno:" + chave, k -> Resultado.ok("SBX-E-" + Math.abs(chave.hashCode())));
    }
}
