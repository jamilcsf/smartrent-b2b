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
}
