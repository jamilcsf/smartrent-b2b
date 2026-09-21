package br.com.unisenai.smartrent.security;

import br.com.unisenai.smartrent.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Emite e valida os tokens JWT da aplicacao. */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long validadeSegundos;

    public JwtService(@Value("${smartrent.jwt.secret}") String segredo,
                      @Value("${smartrent.jwt.expiracao-segundos}") long validadeSegundos) {
        // RNF03: o segredo vem do ambiente, nunca do codigo-fonte.
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validadeSegundos = validadeSegundos;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("nome", usuario.getNome())
                .claim("papel", usuario.getPapel().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(validadeSegundos)))
                .signWith(chave)
                .compact();
    }

    /**
     * Devolve o e-mail contido num token valido, ou {@code null} se o token
     * estiver ausente, malformado, expirado ou com assinatura invalida.
     *
     * <p>Devolver null em vez de lancar excecao e deliberado: no soft gating,
     * um token ruim apenas torna a requisicao anonima, sem derrubar o acesso
     * as rotas publicas.
     */
    public String emailDoTokenOuNull(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public long getValidadeSegundos() {
        return validadeSegundos;
    }
}
