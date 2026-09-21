package br.com.unisenai.smartrent.dto;

/** Token emitido no login ou no cadastro, com os dados de exibicao do usuario. */
public record AuthResponse(String token, long expiraEmSegundos, UsuarioResponse usuario) {
}
