package br.com.unisenai.smartrent.service;

import java.math.BigDecimal;

/**
 * Porta de saida para o provedor de pagamentos. O projeto ainda nao tem um
 * provedor real: a implementacao padrao e um sandbox ({@link GatewayPagamentoSandbox}).
 * Trocar por Mercado Pago, Stripe etc. e escrever outra implementacao; as regras
 * de reserva, cancelamento e reembolso nao mudam.
 *
 * <p>Toda operacao recebe uma chave de idempotencia: repetir a chamada com a
 * mesma chave devolve o mesmo resultado, sem cobrar nem estornar de novo.
 */
public interface GatewayPagamento {

    Resultado cobrar(String chaveIdempotencia, BigDecimal valor, String moeda, String tokenMeioDePagamento);

    /** Estorna para o mesmo meio de pagamento da cobranca original. */
    Resultado estornar(String chaveIdempotencia, String refCobranca, BigDecimal valor);

    record Resultado(boolean aprovado, String referencia, String motivo) {
        public static Resultado ok(String ref) {
            return new Resultado(true, ref, null);
        }

        public static Resultado recusado(String motivo) {
            return new Resultado(false, null, motivo);
        }
    }
}
