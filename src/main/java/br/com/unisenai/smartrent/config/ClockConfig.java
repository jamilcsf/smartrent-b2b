package br.com.unisenai.smartrent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relogio injetavel. Toda regra que depende de "agora" (janela de 24h,
 * republicacao em 2h, lembretes) le o tempo daqui, o que permite testar essas
 * regras com um relogio fixo em vez de dormir de verdade.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
