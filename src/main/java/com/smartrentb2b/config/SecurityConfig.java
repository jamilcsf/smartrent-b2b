package com.smartrentb2b.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança mínima e temporária.
 *
 * <p>Sem ela, o Spring Security protege todos os endpoints com basic auth e uma
 * senha aleatória regenerada a cada boot, o que inviabiliza qualquer teste
 * manual enquanto a camada de Service e o fluxo de autenticação não existirem.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // TODO: restringir quando o serviço de autenticação estiver pronto
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
