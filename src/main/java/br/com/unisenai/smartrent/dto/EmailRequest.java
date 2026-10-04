package br.com.unisenai.smartrent.dto;

/** Pedido de troca de e-mail do proprio usuario, com a reautenticacao (senha atual ou credencial Google). */
public record EmailRequest(String novoEmail, String senhaAtual, String credencialGoogle) {

    /** Fixo: um registro automatico do objeto nunca pode imprimir senha ou e-mail. */
    @Override
    public String toString() {
        return "EmailRequest[***]";
    }
}
