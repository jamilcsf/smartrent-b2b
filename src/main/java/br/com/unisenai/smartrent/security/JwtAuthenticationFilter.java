package br.com.unisenai.smartrent.security;

import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Le o cabecalho {@code Authorization} e, havendo token valido, identifica o
 * usuario na requisicao.
 *
 * <p>Nunca interrompe a cadeia: ausencia ou invalidez de token apenas deixa a
 * requisicao anonima. E o que sustenta o soft gating — o site continua
 * navegavel para visitantes, e o bloqueio fica a cargo das regras de cada
 * rota, nao deste filtro.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");

        if (cabecalho != null && cabecalho.startsWith(PREFIXO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String email = jwtService.emailDoTokenOuNull(cabecalho.substring(PREFIXO.length()));
            if (email != null) {
                Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
                usuario.filter(Usuario::isAtivo).ifPresent(u -> {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getPapel().name()));
                    var auth = new UsernamePasswordAuthenticationToken(u, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}
