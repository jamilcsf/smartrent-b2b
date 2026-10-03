package br.com.unisenai.smartrent.dto;

/**
 * Configuração pública que o front precisa para montar as telas de acesso.
 * Só chaves públicas: o segredo do captcha nunca sai do servidor.
 */
public record AuthConfigResponse(String recaptchaSiteKey, String googleClientId) {
}
