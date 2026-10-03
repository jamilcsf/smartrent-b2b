package br.com.unisenai.smartrent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.OptionalDouble;

/**
 * Processamento sem FFmpeg (padrao quando o binario nao existe): valida o MP4/MOV
 * lendo o cabecalho {@code moov/mvhd} e publica o proprio arquivo, sem
 * transcodificar, sem HLS, sem poster e sem remover metadados. E um recurso de
 * desenvolvimento e de emergencia: em producao configure o FFmpeg
 * ({@code smartrent.video.ffmpeg-path}).
 */
public class VideoProcessingBasico implements VideoProcessingService {

    @Override
    public Metadados inspecionar(Path arquivo) {
        OptionalDouble duracao = Mp4Duracao.segundos(arquivo);
        if (duracao.isEmpty()) {
            throw new VideoInvalidoException("Não foi possível ler a duração do vídeo. Envie um MP4 ou MOV válido.");
        }
        return new Metadados(duracao.getAsDouble(), 0, 0, "mp4");
    }

    @Override
    public Resultado processar(Path original, Path pastaSaida) throws IOException {
        Files.createDirectories(pastaSaida);
        Path mp4 = pastaSaida.resolve("video.mp4");
        Files.copy(original, mp4, StandardCopyOption.REPLACE_EXISTING);
        return new Resultado(mp4, null, null, inspecionar(original));
    }

    @Override
    public String descricao() {
        return "basico (sem FFmpeg: sem transcodificacao, HLS, poster nem remocao de metadados)";
    }

    @Override
    public boolean aceitaWebm() {
        return false;
    }
}
