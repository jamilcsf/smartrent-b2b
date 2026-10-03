package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Prazos do ciclo de vida do anuncio. Todos vem do ambiente
 * (smartrent.anuncio.*), com os valores do produto como padrao.
 *
 * @param janelaPrePublicacaoHoras horas entre a primeira confirmacao de preco e o direito de publicar
 * @param republicacaoHoras        horas fora do ar depois de confirmar uma edicao
 * @param lembreteAposHoras        horas em EM_EDICAO ate o primeiro lembrete
 * @param lembreteIntervaloHoras   intervalo entre lembretes seguintes
 * @param lembreteMax              numero maximo de lembretes por edicao
 */
@ConfigurationProperties(prefix = "smartrent.anuncio")
public record AnuncioProperties(
        @DefaultValue("24") long janelaPrePublicacaoHoras,
        @DefaultValue("2") long republicacaoHoras,
        @DefaultValue("24") long lembreteAposHoras,
        @DefaultValue("24") long lembreteIntervaloHoras,
        @DefaultValue("5") int lembreteMax) {
}
