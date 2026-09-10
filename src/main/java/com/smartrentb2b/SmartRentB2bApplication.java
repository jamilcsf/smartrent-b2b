package com.smartrentb2b;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação SmartRent B2B.
 *
 * <p>Por estar no pacote raiz {@code com.smartrentb2b}, o component scan e o
 * scan de entidades/repositórios JPA alcançam automaticamente
 * {@code domain.model}, {@code repository} e {@code config}.
 */
@SpringBootApplication
public class SmartRentB2bApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRentB2bApplication.class, args);
    }
}
