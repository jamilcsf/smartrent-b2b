package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;

/** Usuario devolvido pela API. Nunca inclui o hash da senha. */
public record UsuarioResponse(Long id, String nome, String email, PapelUsuario papel) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel());
    }
}
