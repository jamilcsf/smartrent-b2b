package br.com.unisenai.smartrent.security;

import br.com.unisenai.smartrent.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Emite e valida os tokens JWT da aplicacao. O bean e criado por {@code JwtConfig}, que valida o segredo. */
public class JwtService {

    private final SecretKey chave;
    private final long validadeSegundos;

    public JwtService(String segredo, long validadeSegundos) {
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
                .claim("sv", usuario.getSessaoVersao())
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

    /**
     * Versao de sessao gravada no token (0 para tokens antigos, sem a claim).
     * O filtro compara com a do usuario: trocar senha/e-mail invalida os demais tokens.
     */
    public int versaoDaSessao(String token) {
        try {
            Object sv = Jwts.parser().verifyWith(chave).build()
                    .parseSignedClaims(token).getPayload().get("sv");
            return sv instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public long getValidadeSegundos() {
        return validadeSegundos;
    }
}
