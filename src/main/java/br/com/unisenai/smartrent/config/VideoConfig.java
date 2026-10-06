package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.service.ExecutorDeComando;
import br.com.unisenai.smartrent.service.ExecutorDeProcesso;
import br.com.unisenai.smartrent.service.VideoProcessingBasico;
import br.com.unisenai.smartrent.service.VideoProcessingIndisponivel;
import br.com.unisenai.smartrent.service.VideoProcessingFfmpeg;
import br.com.unisenai.smartrent.service.VideoProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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
    public VideoProcessingService videoProcessingService(VideoProperties props, ExecutorDeComando executor,
                                                         Environment ambiente) {
        VideoProcessingService servico = escolher(props, executor, ambiente.acceptsProfiles(Profiles.of("dev", "test")));
        if (servico instanceof VideoProcessingIndisponivel) {
            log.error("FFmpeg nao encontrado: os envios de video serao RECUSADOS (sem ele os metadados e o GPS do video "
                    + "nao sao removidos). Instale o FFmpeg ou defina VIDEO_FFMPEG_PATH.");
        }
        log.info("Processamento de video: {}", servico.descricao());
        return servico;
    }

    /** FFmpeg quando existe; sem ele, o modo basico so em dev/test; fora disso, nenhum video e aceito. */
    static VideoProcessingService escolher(VideoProperties props, ExecutorDeComando executor, boolean devOuTeste) {
        String ffmpeg = props.ffmpegPath().isBlank() ? "ffmpeg" : props.ffmpegPath();
        String ffprobe = props.ffprobePath().isBlank() ? derivarFfprobe(ffmpeg) : props.ffprobePath();
        if (existe(executor, ffmpeg) && existe(executor, ffprobe)) {
            return new VideoProcessingFfmpeg(ffmpeg, ffprobe, executor, props);
        }
        return devOuTeste ? new VideoProcessingBasico() : new VideoProcessingIndisponivel();
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
