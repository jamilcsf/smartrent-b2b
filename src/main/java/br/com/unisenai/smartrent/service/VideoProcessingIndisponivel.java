package br.com.unisenai.smartrent.service;

import java.nio.file.Path;

/**
 * Usado fora de dev/test quando nao ha FFmpeg: sem ele nenhum metadado (inclusive a localizacao GPS gravada pelo
 * celular) e removido do video, entao nenhum video e aceito em vez de publicar um arquivo que vaza a localizacao.
 */
public class VideoProcessingIndisponivel implements VideoProcessingService {

    static final String MENSAGEM = "O envio de vídeo está temporariamente indisponível. Tente novamente mais tarde.";

    @Override
    public Metadados inspecionar(Path arquivo) {
        throw new VideoInvalidoException(MENSAGEM);
    }

    @Override
    public Resultado processar(Path original, Path pastaSaida) {
        throw new VideoInvalidoException(MENSAGEM);
    }

    @Override
    public String descricao() {
        return "INDISPONIVEL (sem FFmpeg fora de dev/test: videos recusados para nao publicar metadados nem GPS)";
    }

    @Override
    public boolean aceitaWebm() {
        return false;
    }
}
