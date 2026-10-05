package br.com.unisenai.smartrent.security;

/** Valor cifrado que nao pode ser lido (adulterado, chave errada ou versao desconhecida). A mensagem nunca carrega conteudo. */
public class FalhaDecifragemException extends RuntimeException {

    public FalhaDecifragemException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public FalhaDecifragemException(String mensagem) {
        super(mensagem);
    }
}
