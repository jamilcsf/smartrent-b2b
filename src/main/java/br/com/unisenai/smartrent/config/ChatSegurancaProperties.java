package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Analise de comportamento do SmartChat (smartrent.chat.*, ao lado de {@link ChatProperties}).
 *
 * @param hmacKey                     SMARTCHAT_HMAC_KEY: Base64 de pelo menos 32 bytes; chave do HMAC-SHA-256 usado para
 *                                    comparar textos sem guarda-los. Nunca registrar em log
 * @param envioMassaConversas         N: conversas distintas que recebem o mesmo texto do mesmo autor para disparar o alerta
 * @param envioMassaConversasContaNova mesmo limite para conta criada ha menos de {@code contaNovaDias} (peso maior: limiar menor)
 * @param envioMassaMinutos           M: janela, em minutos, da contagem
 * @param contaNovaDias               X: ate quantos dias de vida a conta conta como nova
 * @param limiteRestritoPorMinuto     mensagens por minuto permitidas enquanto o usuario esta com o limite restrito
 * @param limiteRestritoMinutos       por quanto tempo o limite restrito vale depois do alerta de envio em massa
 * @param alertaJanelaMinutos         janela em que o mesmo tipo de alerta nao se repete para o mesmo usuario
 */
@ConfigurationProperties(prefix = "smartrent.chat")
public record ChatSegurancaProperties(
        @DefaultValue("") String hmacKey,
        @DefaultValue("5") int envioMassaConversas,
        @DefaultValue("3") int envioMassaConversasContaNova,
        @DefaultValue("10") int envioMassaMinutos,
        @DefaultValue("7") int contaNovaDias,
        @DefaultValue("3") int limiteRestritoPorMinuto,
        @DefaultValue("60") int limiteRestritoMinutos,
        @DefaultValue("60") int alertaJanelaMinutos) {

    public static ChatSegurancaProperties padrao() {
        return new ChatSegurancaProperties("", 5, 3, 10, 7, 3, 60, 60);
    }

    /** A chave nao pode aparecer em log nem em dump de configuracao. */
    @Override
    public String toString() {
        return "ChatSegurancaProperties[hmacKey=****]";
    }
}
