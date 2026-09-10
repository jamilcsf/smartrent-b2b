package com.smartrentb2b.domain.model.enums;

/**
 * Papéis de acesso reconhecidos pela plataforma SmartRent B2B.
 * Mapeado para {@code GrantedAuthority} como {@code ROLE_<PAPEL>}
 * na integração com a autenticação herdada do Projeto Aplicado III.
 */
public enum PapelUsuario {

    /** Administrador da plataforma (suporte, auditoria, gestão de contas). */
    ADMIN,

    /** Pequeno anfitrião / administrador de imóveis — usuário principal do MVP. */
    ANFITRIAO
}