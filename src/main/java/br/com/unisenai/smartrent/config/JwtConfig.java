package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.charset.StandardCharsets;

/**
 * Cria o {@link JwtService} a partir de {@code smartrent.jwt.secret} (JWT_SECRET).
 *
 * <p>Fora dos perfis {@code dev} e {@code test} a aplicacao nao sobe sem o segredo, com menos de 32 bytes ou
 * com o segredo publico de desenvolvimento: quem conhece o valor do repositorio forjaria tokens de qualquer
 * usuario, inclusive ADMIN. O valor nunca e registrado em log nem aparece nas mensagens de erro.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    static final int TAMANHO_MINIMO_BYTES = 32;

    /** Segredo PUBLICO de desenvolvimento: nao protege nada. */
    static final String SEGREDO_DESENVOLVIMENTO = "desenvolvimento-local-trocar-em-producao-min-32-bytes";

    @Bean
    public JwtService jwtService(@Value("${smartrent.jwt.secret:}") String segredo,
                                 @Value("${smartrent.jwt.expiracao-segundos}") long validadeSegundos,
                                 Environment ambiente) {
        return new JwtService(resolver(segredo, ambiente.acceptsProfiles(Profiles.of("dev", "test"))), validadeSegundos);
    }

    static String resolver(String segredo, boolean perfilDevOuTeste) {
        boolean ausente = segredo == null || segredo.isBlank();
        if (perfilDevOuTeste) {
            if (ausente) {
                log.warn("JWT_SECRET ausente: usando o segredo publico de desenvolvimento (perfil dev/test). "
                        + "Nao use fora da maquina local.");
                return SEGREDO_DESENVOLVIMENTO;
            }
            return segredo;
        }
        if (ausente || SEGREDO_DESENVOLVIMENTO.equals(segredo)) {
            throw new IllegalStateException("JWT_SECRET ausente ou igual ao segredo publico de desenvolvimento."
                    + " Defina a variavel de ambiente com pelo menos 32 bytes (ex.: openssl rand -base64 32)."
                    + " Para desenvolvimento local, ative o perfil 'dev' (SPRING_PROFILES_ACTIVE=dev).");
        }
        if (segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_BYTES) {
            throw new IllegalStateException("JWT_SECRET curto demais: use pelo menos " + TAMANHO_MINIMO_BYTES
                    + " bytes (ex.: openssl rand -base64 32).");
        }
        return segredo;
    }
}
