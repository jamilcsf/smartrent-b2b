package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuracao do SmartChat (smartrent.chat.*).
 *
 * @param dominiosPermitidos       dominios proprios cujos links NAO sao borrados (e seus subdominios)
 * @param termosArquivo            arquivo opcional de termos ofensivos/sexuais (substitui a lista padrao)
 * @param bloqueioAtivo            SMARTCHAT_BLOCK_ENFORCEMENT: aplicar o bloqueio de usuario (protótipo: desligado)
 * @param maxCaracteres            tamanho maximo de uma mensagem
 * @param mensagensPorMinuto       limite por usuario (rate limit)
 * @param notificacaoAgrupamentoMin minutos sem nova notificacao de mensagem por conversa (evita um e-mail por mensagem)
 * @param denunciaIntervaloSeg     janela em que denuncias/bloqueios identicos sao rejeitados como duplicata
 */
@ConfigurationProperties(prefix = "smartrent.chat")
public record ChatProperties(
        @DefaultValue({"smartrent.com.br", "localhost"}) List<String> dominiosPermitidos,
        @DefaultValue("") String termosArquivo,
        @DefaultValue("false") boolean bloqueioAtivo,
        @DefaultValue("1000") int maxCaracteres,
        @DefaultValue("20") int mensagensPorMinuto,
        @DefaultValue("10") int notificacaoAgrupamentoMin,
        @DefaultValue("300") int denunciaIntervaloSeg) {

    public static ChatProperties padrao() {
        return new ChatProperties(List.of("smartrent.com.br", "localhost"), "", false, 1000, 20, 10, 300);
    }
}
