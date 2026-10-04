package br.com.unisenai.smartrent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os jobs agendados. Desligavel por smartrent.jobs.habilitado=false. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smartrent.jobs.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendamentoConfig {
}
