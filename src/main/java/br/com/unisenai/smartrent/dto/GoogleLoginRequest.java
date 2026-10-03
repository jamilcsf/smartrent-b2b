package br.com.unisenai.smartrent.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "A credencial do Google é obrigatória.") String credential) {
}
