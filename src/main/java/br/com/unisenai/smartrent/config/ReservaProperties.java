package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regras operacionais da reserva autoatendida (smartrent.reserva.*).
 *
 * @param pendenteExpiraMinutos minutos que uma reserva pendente segura as datas antes de expirar sem cobranca
 */
@ConfigurationProperties(prefix = "smartrent.reserva")
public record ReservaProperties(@DefaultValue("30") long pendenteExpiraMinutos) {
}
