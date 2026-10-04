package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;

/**
 * Parametros da politica de cancelamento e reembolso (smartrent.cancelamento.*).
 * Os valores vigentes na criacao de cada reserva sao gravados no snapshot dela:
 * mudar a configuracao depois nao afeta reservas existentes.
 *
 * @param antecedenciaHoras horas antes do check-in ate as quais o reembolso e integral
 * @param regretDias        direito de arrependimento (CANCEL_REGRET_DAYS); 0 = desligado e sem efeito.
 *                          Item em stand-by do setor juridico: nao ligar por conta propria.
 * @param versao            versao do texto da politica (texto provisorio, pendente de revisao juridica)
 * @param checkinHora       horario padrao de check-in (Brasilia), base do prazo de antecedencia
 * @param checkoutHora      horario padrao de check-out (Brasilia)
 */
@ConfigurationProperties(prefix = "smartrent.cancelamento")
public record PoliticaCancelamentoProperties(
        @DefaultValue("48") int antecedenciaHoras,
        @DefaultValue("0") int regretDias,
        @DefaultValue("PROVISORIA-1") String versao,
        @DefaultValue("14:00") LocalTime checkinHora,
        @DefaultValue("11:00") LocalTime checkoutHora) {

    public static PoliticaCancelamentoProperties padrao() {
        return new PoliticaCancelamentoProperties(48, 0, "PROVISORIA-1", LocalTime.of(14, 0), LocalTime.of(11, 0));
    }
}
