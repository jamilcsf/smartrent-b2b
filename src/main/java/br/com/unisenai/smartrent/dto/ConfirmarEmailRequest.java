package br.com.unisenai.smartrent.dto;

/** Token do link de confirmacao. Fixo no toString para o token nunca ir parar em log. */
public record ConfirmarEmailRequest(String token) {

    @Override
    public String toString() {
        return "ConfirmarEmailRequest[***]";
    }
}
