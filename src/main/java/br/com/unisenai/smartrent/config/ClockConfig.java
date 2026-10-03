package br.com.unisenai.smartrent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relogio injetavel, sempre no fuso da plataforma ({@code smartrent.timezone},
 * padrao America/Sao_Paulo) e nao no da JVM. Toda regra que depende de "agora"
 * ou de "hoje" le o tempo daqui, o que permite testar prazos com um relogio
 * controlado. Duracoes (24h, 2h, lembretes) continuam sendo tempo decorrido; o
 * fuso so define o que e "hoje" e como as datas sao exibidas.
 */
@Configuration
public class ClockConfig {

    @Bean
    public ZoneId zonaDaPlataforma(@Value("${smartrent.timezone:" + PlataformaTempo.PADRAO + "}") String nome) {
        ZoneId zona = ZoneId.of(nome); // nome invalido derruba a subida: melhor que datas erradas
        PlataformaTempo.definir(zona);
        return zona;
    }

    @Bean
    public Clock clock(ZoneId zonaDaPlataforma) {
        return Clock.system(zonaDaPlataforma);
    }
}
