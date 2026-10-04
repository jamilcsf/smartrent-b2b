package br.com.unisenai.smartrent.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "A credencial do Google é obrigatória.") String credential,
        /** Só vale quando a conta ainda não existe: "GESTOR" ou "CLIENTE". */
        String perfil) {

    public GoogleLoginRequest(String credential) {
        this(credential, null);
    }
}
