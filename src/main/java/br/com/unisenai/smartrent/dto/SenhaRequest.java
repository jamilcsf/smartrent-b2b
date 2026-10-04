package br.com.unisenai.smartrent.dto;

/**
 * Troca (ou definicao) de senha do proprio usuario. {@code senhaAtual} vale para contas com
 * senha; {@code credencialGoogle} substitui a senha atual em contas criadas pelo Google.
 *
 * <p>O {@code toString} e fixo de proposito: um registro automatico do objeto (log, excecao,
 * depuracao) jamais pode imprimir as senhas.
 */
public record SenhaRequest(String senhaAtual, String credencialGoogle, String novaSenha, String confirmacaoSenha) {

    @Override
    public String toString() {
        return "SenhaRequest[***]";
    }
}
