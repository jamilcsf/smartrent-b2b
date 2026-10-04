package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;

import java.util.List;

/**
 * Usuario devolvido pela API. Nunca inclui o hash da senha. {@code restricoes} traz os
 * codigos das restricoes temporarias ativas (vazio quando nao ha), para os paineis
 * mostrarem o aviso fixo.
 */
public record UsuarioResponse(Long id, String nome, String email, PapelUsuario papel, boolean smartchatLiberado,
                              String fotoUrl, List<String> restricoes) {

    public UsuarioResponse(Long id, String nome, String email, PapelUsuario papel, boolean smartchatLiberado) {
        this(id, nome, email, papel, smartchatLiberado, null, List.of());
    }

    public static UsuarioResponse de(Usuario u) {
        return de(u, List.of());
    }

    public static UsuarioResponse de(Usuario u, List<String> restricoes) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isSmartchatLiberado(),
                fotoUrl(u), restricoes);
    }

    /** URL publica da foto (nome aleatorio + versao contra cache antigo), ou nulo sem foto. */
    public static String fotoUrl(Usuario u) {
        return u.getFotoArquivo() == null ? null
                : "/api/perfil/foto/" + u.getFotoArquivo() + "?v=" + u.getFotoVersao();
    }
}
