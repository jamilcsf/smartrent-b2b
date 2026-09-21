package br.com.unisenai.smartrent.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class GroqApiClient {
    public BigDecimal buscarSugestaoIA(Long imovelId) {
        // Integração mockada para chamadas à API externa Groq/Llama
        return new BigDecimal("250.00");
    }
}
