package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Armazenamento e limites de tamanho das midias (smartrent.midia.*). Os
 * limites de quantidade (14 imagens, 2 videos) e de duracao (2 min) sao regra
 * de produto e ficam no codigo, em MidiaValidador.
 */
@ConfigurationProperties(prefix = "smartrent.midia")
public record MidiaProperties(
        @DefaultValue("./dados/midias") String diretorio,
        @DefaultValue("10") long tamanhoMaxImagemMb,
        @DefaultValue("100") long tamanhoMaxVideoMb) {
}
