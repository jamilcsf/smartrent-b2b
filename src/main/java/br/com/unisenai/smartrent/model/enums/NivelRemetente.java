package br.com.unisenai.smartrent.model.enums;

/** Em nome de quem a administracao fala com o usuario. O rotulo e o que aparece no aviso. */
public enum NivelRemetente {
    ADMINISTRACAO("Administração da plataforma"),
    MODERACAO("Moderação"),
    SUPORTE("Suporte"),
    SEGURANCA("Segurança e privacidade");

    public final String rotulo;

    NivelRemetente(String rotulo) {
        this.rotulo = rotulo;
    }
}
