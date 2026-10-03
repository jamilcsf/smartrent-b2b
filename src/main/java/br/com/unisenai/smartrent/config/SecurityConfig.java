package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Soft gating: o site e publico por padrao.
 *
 * <p>Todas as rotas ficam liberadas; a sessao e stateless e o token, quando
 * presente e valido, apenas identifica quem esta navegando. Rotas que exigem
 * identificacao sao declaradas explicitamente — hoje apenas
 * {@code /api/auth/me}, que serve de gatilho real de 401 para o tratamento
 * global do front, e as areas do gestor ({@code /api/gestor/**} e
 * {@code /api/reservas/**}), que alem de autenticar exigem papel de gestor.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        // Autorizacao real fica aqui, no servidor: esconder
                        // botao no front nao protege endpoint nenhum. Alem do
                        // papel, cada servico confere que o imovel e do gestor.
                        .requestMatchers("/api/gestor/**", "/api/reservas/**")
                                .hasAnyRole("ANFITRIAO", "ADMIN")
                        .anyRequest().permitAll())
                // Sem isto o Spring Security responderia 401 com WWW-Authenticate
                // e o navegador abriria a caixa de dialogo do basic auth.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPointJson())
                        .accessDeniedHandler(acessoNegadoJson()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint entryPointJson() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(),
                    Map.of("erro", "Autenticacao necessaria para esta operacao."));
        };
    }

    /** Autenticado, mas sem o papel exigido: 403 em JSON, nao a pagina de erro padrao. */
    @Bean
    public AccessDeniedHandler acessoNegadoJson() {
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, negado) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(),
                    Map.of("erro", "Voce nao tem permissao para esta operacao."));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
