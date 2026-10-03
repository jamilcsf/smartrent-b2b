package br.com.unisenai.smartrent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Videos do anuncio (smartrent.video.*). Tudo configuravel; o limite de duracao
 * de 90 segundos e regra de produto (substitui a de 2 minutos) mas tambem pode
 * ser ajustado por ambiente.
 *
 * @param ffmpegPath         caminho do FFmpeg; vazio = procura "ffmpeg" no PATH e, se nao achar, usa o processamento basico
 * @param ffprobePath        caminho do ffprobe (idem)
 * @param duracaoMaxSegundos duracao maxima de cada video (90 = 1:30)
 * @param tamanhoMaxMb       tamanho maximo do arquivo enviado
 * @param alturaMaxEntrada   resolucao maxima aceita na entrada (altura em pixels; 2160 = 4K)
 * @param parteMb            tamanho das partes do envio retomavel
 * @param apagarOriginal     apagar o arquivo original depois do processamento com sucesso
 * @param timeoutSegundos    tempo maximo de cada execucao do FFmpeg/ffprobe
 * @param maxTentativas      tentativas do processamento antes de marcar FALHA
 * @param orfaoHoras         envios iniciados e nunca concluidos sao apagados depois disto
 */
@ConfigurationProperties(prefix = "smartrent.video")
public record VideoProperties(
        @DefaultValue("") String ffmpegPath,
        @DefaultValue("") String ffprobePath,
        @DefaultValue("90") int duracaoMaxSegundos,
        @DefaultValue("500") long tamanhoMaxMb,
        @DefaultValue("2160") int alturaMaxEntrada,
        @DefaultValue("5") int parteMb,
        @DefaultValue("true") boolean apagarOriginal,
        @DefaultValue("600") int timeoutSegundos,
        @DefaultValue("3") int maxTentativas,
        @DefaultValue("24") int orfaoHoras) {

    public static VideoProperties padrao() {
        return new VideoProperties("", "", 90, 500, 2160, 5, true, 600, 3, 24);
    }

    /** "1:30" para exibir limites e duracoes nas mensagens de erro. */
    public static String formatar(long segundos) {
        return (segundos / 60) + ":" + String.format("%02d", segundos % 60);
    }
}
