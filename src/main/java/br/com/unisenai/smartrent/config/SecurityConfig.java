package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
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

    /** Diretivas seguras com o front atual: sem plugins, sem <base> trocado, sem embutir em iframe, formularios so para o proprio site. */
    static final String CSP = "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // usa o CorsConfigurationSource abaixo (sem origens = so mesma origem)
                // Cabecalhos: nosniff, X-Frame-Options DENY e HSTS (em HTTPS) ja vem por padrao. A CSP abaixo so traz
                // as diretivas que NAO quebram as paginas atuais (Tailwind/Chart.js por CDN e scripts inline exigem
                // 'unsafe-inline'): a CSP completa, com nonce, e Visao Futura (V2), ver ADR-008.
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        // Autorizacao real fica aqui, no servidor: esconder
                        // botao no front nao protege endpoint nenhum. Alem do
                        // papel, cada servico confere que o imovel e do gestor.
                        .requestMatchers("/api/gestor/**", "/api/reservas/**", "/api/precificacao/**")
                                .hasAnyRole("ANFITRIAO", "ADMIN")
                        // Reserva do cliente e SmartChat: qualquer usuario autenticado; a
                        // propriedade (so as proprias reservas e conversas) e conferida nos servicos.
                        // O fluxo SSE usa ticket de uso unico (EventSource nao envia Authorization).
                        .requestMatchers("/api/smartchat/stream").permitAll()
                        .requestMatchers("/api/cliente/**", "/api/smartchat/**").authenticated()
                        // Foto de perfil e exibida a outros usuarios (nome de arquivo imprevisivel);
                        // o resto do perfil e sempre do proprio usuario autenticado.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/perfil/foto/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/perfil/exclusao-dados/cancelar").permitAll()
                        .requestMatchers("/api/perfil/email/confirmar", "/api/perfil/email/verificar").permitAll()
                        .requestMatchers("/api/perfil/**").authenticated()
                        .anyRequest().permitAll())
                // Sem isto o Spring Security responderia 401 com WWW-Authenticate
                // e o navegador abriria a caixa de dialogo do basic auth.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPointJson())
                        .accessDeniedHandler(acessoNegadoJson()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS so por configuracao: o front e servido pela propria aplicacao (mesma origem), entao sem
     * SMARTRENT_CORS_ORIGENS nenhuma origem externa e liberada (antes, {@code @CrossOrigin("*")} em alguns controllers).
     * Sem cookies nem credenciais: a autenticacao e por Bearer.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${smartrent.cors.origens:}") String origens) {
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        List<String> permitidas = Arrays.stream(origens.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (!permitidas.isEmpty()) {
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(permitidas);
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            c.setAllowCredentials(false);
            c.setMaxAge(3600L);
            fonte.registerCorsConfiguration("/api/**", c);
        }
        return fonte;
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
