package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.service.ExecutorDeComando;
import br.com.unisenai.smartrent.service.ExecutorDeProcesso;
import br.com.unisenai.smartrent.service.VideoProcessingBasico;
import br.com.unisenai.smartrent.service.VideoProcessingFfmpeg;
import br.com.unisenai.smartrent.service.VideoProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Escolhe a implementacao de {@link VideoProcessingService}: FFmpeg quando o
 * binario existe (caminho configurado ou "ffmpeg" no PATH) e o processamento
 * basico quando nao existe. Trocar por um servico gerenciado (Mux, Cloudflare
 * Stream, MediaConvert) e registrar outro bean desta interface.
 */
@Configuration
public class VideoConfig {

    private static final Logger log = LoggerFactory.getLogger(VideoConfig.class);

    @Bean
    public ExecutorDeComando executorDeComando() {
        return new ExecutorDeProcesso();
    }

    @Bean
    public VideoProcessingService videoProcessingService(VideoProperties props, ExecutorDeComando executor) {
        String ffmpeg = props.ffmpegPath().isBlank() ? "ffmpeg" : props.ffmpegPath();
        String ffprobe = props.ffprobePath().isBlank() ? derivarFfprobe(ffmpeg) : props.ffprobePath();
        VideoProcessingService servico = existe(executor, ffmpeg) && existe(executor, ffprobe)
                ? new VideoProcessingFfmpeg(ffmpeg, ffprobe, executor, props)
                : new VideoProcessingBasico();
        log.info("Processamento de video: {}", servico.descricao());
        return servico;
    }

    private static String derivarFfprobe(String ffmpeg) {
        return ffmpeg.endsWith("ffmpeg") || ffmpeg.endsWith("ffmpeg.exe")
                ? ffmpeg.replaceAll("ffmpeg(\\.exe)?$", "ffprobe$1") : "ffprobe";
    }

    private static boolean existe(ExecutorDeComando executor, String programa) {
        try {
            return executor.executar(List.of(programa, "-version"), Duration.ofSeconds(10)).codigo() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
