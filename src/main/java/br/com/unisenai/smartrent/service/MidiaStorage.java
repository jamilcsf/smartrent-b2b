package br.com.unisenai.smartrent.service;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Onde os bytes das midias vivem. As regras (limites, validacao, estados)
 * nao sabem se o destino e disco ou um bucket: trocar por S3 e escrever outra
 * implementacao desta interface.
 */
public interface MidiaStorage {

    /** Guarda o arquivo local sob o nome dado (sobrescreve se existir). */
    void salvar(String nome, Path origem) throws IOException;

    /** Abre o arquivo para leitura; suporta Range quando o destino permite. */
    Resource abrir(String nome);

    /** Remove o arquivo. Nao falha se ele ja nao existir. */
    void remover(String nome);

    /**
     * Caminho local do arquivo, para o envio em partes e para o FFmpeg. Armazenamentos
     * remotos (S3) nao implementam isto: usam envio direto com URL pre-assinada e um
     * worker que baixa/envia os arquivos.
     */
    Path caminhoLocal(String nome);

    /**
     * Endereco publico direto (CDN/bucket) do arquivo, ou nulo quando ele deve ser entregue pela propria
     * aplicacao. Com endereco, o controller responde 302 e os bytes nao passam pelo servidor.
     */
    default String urlPublica(String nome) {
        return null;
    }

    /** Remove tudo sob o prefixo (pasta de um video e suas variantes). Nao falha se nao existir. */
    void removerPrefixo(String prefixo);
}
