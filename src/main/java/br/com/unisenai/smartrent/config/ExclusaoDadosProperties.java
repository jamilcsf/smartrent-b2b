package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Solicitacao de exclusao de dados (smartrent.exclusao-dados.*). Os prazos sao configuracao,
 * nao decisao juridica: definir o periodo minimo de analise e o que e retido e do setor responsavel.
 *
 * @param minAnaliseHoras      DATA_DELETION_MIN_REVIEW_HOURS: antes disto a equipe nao pode aprovar
 * @param riscoDias            janela dos sinais de risco (e-mail/senha alterados, IPs conhecidos)
 * @param notificarEmail       DATA_DELETION_NOTIFY_EMAIL: endereco interno avisado a cada pedido (vazio = so log e auditoria)
 * @param restricoesAtivas     DATA_DELETION_RESTRICTIONS_ENABLED: aplica as restricoes temporarias enquanto o pedido esta em andamento
 * @param pedidosPorDia        limite de pedidos por usuario por dia
 */
@ConfigurationProperties(prefix = "smartrent.exclusao-dados")
public record ExclusaoDadosProperties(
        @DefaultValue("48") int minAnaliseHoras,
        @DefaultValue("30") int riscoDias,
        @DefaultValue("") String notificarEmail,
        @DefaultValue("true") boolean restricoesAtivas,
        @DefaultValue("3") int pedidosPorDia) {

    public static ExclusaoDadosProperties padrao() {
        return new ExclusaoDadosProperties(48, 30, "", true, 3);
    }
}
