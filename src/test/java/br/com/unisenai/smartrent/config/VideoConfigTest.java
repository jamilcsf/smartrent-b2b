package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.service.ExecutorDeComando;
import br.com.unisenai.smartrent.service.VideoProcessingBasico;
import br.com.unisenai.smartrent.service.VideoProcessingFfmpeg;
import br.com.unisenai.smartrent.service.VideoProcessingIndisponivel;
import br.com.unisenai.smartrent.service.VideoProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Sem FFmpeg o video nao tem metadados/GPS removidos: fora de dev/test nenhum video e aceito. */
class VideoConfigTest {

    private static final ExecutorDeComando COM_FFMPEG = (args, limite) -> new ExecutorDeComando.Saida(0, "ffmpeg version");
    private static final ExecutorDeComando SEM_FFMPEG = (args, limite) -> {
        throw new IOException("nao encontrado");
    };

    @Test
    @DisplayName("CT550 - Com FFmpeg, usa o processamento completo em qualquer perfil")
    void comFfmpeg() {
        assertInstanceOf(VideoProcessingFfmpeg.class, VideoConfig.escolher(VideoProperties.padrao(), COM_FFMPEG, false));
        assertInstanceOf(VideoProcessingFfmpeg.class, VideoConfig.escolher(VideoProperties.padrao(), COM_FFMPEG, true));
    }

    @Test
    @DisplayName("CT551 - Sem FFmpeg: dev/test usa o modo basico; fora deles o video e recusado")
    void semFfmpeg() {
        assertInstanceOf(VideoProcessingBasico.class, VideoConfig.escolher(VideoProperties.padrao(), SEM_FFMPEG, true));
        VideoProcessingService producao = VideoConfig.escolher(VideoProperties.padrao(), SEM_FFMPEG, false);
        assertInstanceOf(VideoProcessingIndisponivel.class, producao);
        assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> producao.inspecionar(Path.of("x.mp4")));
        assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> producao.processar(Path.of("x.mp4"), Path.of("saida")));
    }
}
