package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Armazenamento em disco local, na pasta smartrent.midia.diretorio. */
@Component
public class MidiaStorageDisco implements MidiaStorage {

    private static final Logger log = LoggerFactory.getLogger(MidiaStorageDisco.class);

    private final Path raiz;

    public MidiaStorageDisco(MidiaProperties props) {
        this.raiz = Path.of(props.diretorio()).toAbsolutePath().normalize();
    }

    @Override
    public void salvar(String nome, Path origem) throws IOException {
        Path destino = resolver(nome);
        Files.createDirectories(raiz);
        Files.copy(origem, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource abrir(String nome) {
        return new FileSystemResource(resolver(nome));
    }

    @Override
    public void remover(String nome) {
        try {
            Files.deleteIfExists(resolver(nome));
        } catch (IOException e) {
            // Arquivo orfao e lixo, nao erro de negocio: registra e segue.
            log.warn("Nao foi possivel remover a midia {}: {}", nome, e.getMessage());
        }
    }

    /** Impede que um nome com "../" escape da pasta de midias. */
    private Path resolver(String nome) {
        Path caminho = raiz.resolve(nome).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Nome de arquivo invalido.");
        }
        return caminho;
    }
}
