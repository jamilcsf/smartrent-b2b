package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Pagina de perfil (smartrent.perfil.*). Tudo configuravel por ambiente; os padroes
 * sao os do produto.
 *
 * @param fotoMaxMb            tamanho maximo do arquivo da foto enviada
 * @param fotoMinLado          menor lado aceito (pixels)
 * @param fotoMaxPixels        total de pixels aceito (protecao contra decompression bomb; checado antes de decodificar)
 * @param fotoLado             lado da foto final, em pixels (nunca maior que o menor lado da original)
 * @param fotoQualidade        qualidade do JPEG gerado (0 a 1)
 * @param senhaMinima          tamanho minimo da nova senha
 * @param senhasComunsArquivo  arquivo opcional com senhas comuns (substitui a lista padrao do classpath)
 * @param emailTokenMinutos    validade do link de confirmacao de novo e-mail
 * @param senhaTentativas      senhas atuais erradas toleradas por janela
 * @param senhaJanelaMinutos   janela das tentativas erradas
 * @param emailPedidosPorHora  pedidos de troca de e-mail por hora e usuario
 * @param fotoUploadsPorHora   envios de foto por hora e usuario
 * @param baseUrl              endereco publico do site, usado nos links enviados por e-mail
 */
@ConfigurationProperties(prefix = "smartrent.perfil")
public record PerfilProperties(
        @DefaultValue("2") int fotoMaxMb,
        @DefaultValue("128") int fotoMinLado,
        @DefaultValue("25000000") long fotoMaxPixels,
        @DefaultValue("512") int fotoLado,
        @DefaultValue("0.88") float fotoQualidade,
        @DefaultValue("10") int senhaMinima,
        @DefaultValue("") String senhasComunsArquivo,
        @DefaultValue("60") int emailTokenMinutos,
        @DefaultValue("5") int senhaTentativas,
        @DefaultValue("15") int senhaJanelaMinutos,
        @DefaultValue("3") int emailPedidosPorHora,
        @DefaultValue("10") int fotoUploadsPorHora,
        @DefaultValue("http://localhost:8080") String baseUrl) {

    public static PerfilProperties padrao() {
        return new PerfilProperties(2, 128, 25_000_000L, 512, 0.88f, 10, "", 60, 5, 15, 3, 10, "http://localhost:8080");
    }
}
