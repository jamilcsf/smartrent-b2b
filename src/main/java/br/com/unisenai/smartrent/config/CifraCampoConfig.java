package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.security.CifraCampo;
import br.com.unisenai.smartrent.security.HmacTexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cria a cifra do SmartChat a partir de {@code smartrent.chat.crypto-key} (SMARTCHAT_CRYPTO_KEY, Base64 de 32 bytes).
 *
 * <p>Fora dos perfis {@code dev} e {@code test} a aplicacao nao sobe sem a chave (ou com tamanho errado).
 * Nesses perfis, sem chave, usa-se uma chave publica de desenvolvimento (como o segredo padrao do JWT):
 * serve so para a maquina local e jamais protege dado real. A chave nunca e registrada em log.
 */
@Configuration
public class CifraCampoConfig {

    private static final Logger log = LoggerFactory.getLogger(CifraCampoConfig.class);

    /** Chave PUBLICA de desenvolvimento (32 bytes): nao protege nada. */
    static final String CHAVE_DESENVOLVIMENTO = Base64.getEncoder()
            .encodeToString("smartchat-chave-dev-nao-use-prod".getBytes(StandardCharsets.UTF_8));

    @Bean
    public CifraCampo cifraCampo(ChatProperties props, Environment ambiente) {
        return criar(props.cryptoKey(), ambiente.acceptsProfiles(Profiles.of("dev", "test")));
    }

    @Bean
    public HmacTexto hmacTexto(ChatSegurancaProperties props, Environment ambiente) {
        return criarHmac(props.hmacKey(), ambiente.acceptsProfiles(Profiles.of("dev", "test")));
    }

    static HmacTexto criarHmac(String chaveBase64, boolean perfilDevOuTeste) {
        boolean ausente = chaveBase64 == null || chaveBase64.isBlank();
        if (ausente && perfilDevOuTeste) {
            log.warn("SMARTCHAT_HMAC_KEY ausente: usando a chave publica de desenvolvimento (perfil dev/test). "
                    + "Nao use fora da maquina local.");
            return HmacTexto.deBase64(CHAVE_DESENVOLVIMENTO);
        }
        try {
            return HmacTexto.deBase64(chaveBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SMARTCHAT_HMAC_KEY invalida: " + e.getMessage()
                    + " Defina a variavel de ambiente com Base64 de pelo menos 32 bytes (ex.: openssl rand -base64 32)."
                    + " Para desenvolvimento local, ative o perfil 'dev' (SPRING_PROFILES_ACTIVE=dev).");
        }
    }

    static CifraCampo criar(String chaveBase64, boolean perfilDevOuTeste) {
        boolean ausente = chaveBase64 == null || chaveBase64.isBlank();
        if (ausente && perfilDevOuTeste) {
            log.warn("SMARTCHAT_CRYPTO_KEY ausente: usando a chave publica de desenvolvimento (perfil dev/test). "
                    + "Nao use fora da maquina local.");
            return CifraCampo.deBase64(CHAVE_DESENVOLVIMENTO);
        }
        try {
            return CifraCampo.deBase64(chaveBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SMARTCHAT_CRYPTO_KEY invalida: " + e.getMessage()
                    + " Defina a variavel de ambiente com Base64 de 32 bytes (ex.: openssl rand -base64 32)."
                    + " Para desenvolvimento local, ative o perfil 'dev' (SPRING_PROFILES_ACTIVE=dev).");
        }
    }
}
