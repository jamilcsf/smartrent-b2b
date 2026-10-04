package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.VideoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Processamento com FFmpeg/ffprobe: H.264 + AAC (MP4 com faststart, ate 1080p),
 * HLS adaptativo (360p, 720p e 1080p, limitados a resolucao da origem), poster
 * e remocao de todos os metadados (inclusive GPS).
 *
 * <p>Seguranca: os argumentos sao uma lista (sem shell) e os caminhos vem do
 * armazenamento (chaves UUID geradas pelo servidor), nunca do nome do arquivo
 * enviado. Cada chamada tem tempo maximo e o FFmpeg roda com poucas threads.
 */
public class VideoProcessingFfmpeg implements VideoProcessingService {

    private static final int[] ALTURAS_HLS = {360, 720, 1080};
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String ffmpeg;
    private final String ffprobe;
    private final ExecutorDeComando executor;
    private final Duration limite;

    public VideoProcessingFfmpeg(String ffmpeg, String ffprobe, ExecutorDeComando executor, VideoProperties props) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.executor = executor;
        this.limite = Duration.ofSeconds(props.timeoutSegundos());
    }

    // ---------------------------------------------------------------- inspecionar

    @Override
    public Metadados inspecionar(Path arquivo) {
        List<String> args = List.of(ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
                arquivo.toAbsolutePath().toString());
        ExecutorDeComando.Saida saida;
        try {
            saida = executor.executar(args, limite);
        } catch (IOException e) {
            throw new VideoInvalidoException("Não foi possível analisar o vídeo agora. Tente novamente.", e);
        }
        if (saida.codigo() != 0) {
            throw new VideoInvalidoException("O arquivo enviado não é um vídeo válido.");
        }
        try {
            JsonNode raiz = JSON.readTree(saida.texto());
            JsonNode video = null;
            for (JsonNode s : raiz.path("streams")) {
                if ("video".equals(s.path("codec_type").asText()) && !"attached_pic".equals(s.path("disposition").path("attached_pic").asText())) {
                    video = s;
                    break;
                }
            }
            if (video == null) {
                throw new VideoInvalidoException("O arquivo não tem trilha de vídeo.");
            }
            double duracao = raiz.path("format").path("duration").asDouble(0);
            if (duracao <= 0) {
                duracao = video.path("duration").asDouble(0);
            }
            if (duracao <= 0) {
                throw new VideoInvalidoException("Não foi possível ler a duração do vídeo.");
            }
            String formato = raiz.path("format").path("format_name").asText("");
            if (!(formato.contains("mp4") || formato.contains("mov") || formato.contains("matroska") || formato.contains("webm"))) {
                throw new VideoInvalidoException("Formato de vídeo não aceito. Envie MP4, MOV ou WebM.");
            }
            return new Metadados(duracao, video.path("width").asInt(0), video.path("height").asInt(0), formato);
        } catch (IOException e) {
            throw new VideoInvalidoException("Não foi possível interpretar a análise do vídeo.", e);
        }
    }

    // ------------------------------------------------------------------ processar

    @Override
    public Resultado processar(Path original, Path pastaSaida) throws IOException {
        Metadados meta = inspecionar(original);
        Files.createDirectories(pastaSaida);
        String entrada = original.toAbsolutePath().toString();

        Path mp4 = pastaSaida.resolve("video.mp4");
        rodar(comandoMp4(entrada, mp4));

        Path poster = pastaSaida.resolve("poster.jpg");
        rodar(comandoPoster(entrada, poster, meta.duracaoSegundos()));

        Path hls = pastaSaida.resolve("hls");
        Files.createDirectories(hls);
        List<int[]> variantes = new ArrayList<>();
        for (int altura : alturasPara(meta.altura())) {
            rodar(comandoHls(entrada, hls, altura));
            int largura = Math.max(2, Math.round((float) meta.largura() * altura / Math.max(1, meta.altura()) / 2f) * 2);
            variantes.add(new int[]{altura, largura});
        }
        Path mestre = hls.resolve("master.m3u8");
        Files.writeString(mestre, listaMestre(variantes), StandardCharsets.UTF_8);

        return new Resultado(mp4, poster, mestre, meta);
    }

    /** 360p/720p/1080p ate a resolucao da origem (sempre ao menos uma variante). */
    static List<Integer> alturasPara(int alturaOrigem) {
        List<Integer> out = new ArrayList<>();
        for (int a : ALTURAS_HLS) {
            if (a <= Math.max(alturaOrigem, ALTURAS_HLS[0])) {
                out.add(a);
            }
        }
        return out;
    }

    static String listaMestre(List<int[]> variantes) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (int[] v : variantes) {
            long banda = v[0] >= 1080 ? 5_000_000L : v[0] >= 720 ? 2_800_000L : 800_000L;
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(banda).append(",RESOLUTION=").append(v[1]).append('x').append(v[0]).append('\n')
              .append(v[0]).append("p.m3u8\n");
        }
        return sb.toString();
    }

    List<String> comandoMp4(String entrada, Path saida) {
        List<String> a = base(entrada);
        a.addAll(List.of("-map", "0:v:0", "-map", "0:a:0?", "-vf", "scale=-2:'min(1080,ih)'",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-map_metadata", "-1", "-map_chapters", "-1",
                saida.toAbsolutePath().toString()));
        return a;
    }

    List<String> comandoPoster(String entrada, Path saida, double duracao) {
        String instante = String.format(java.util.Locale.ROOT, "%.2f", Math.min(1.0, duracao / 2));
        return new ArrayList<>(List.of(ffmpeg, "-y", "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "2",
                "-ss", instante, "-i", entrada, "-frames:v", "1", "-vf", "scale=-2:'min(720,ih)'", "-q:v", "3",
                "-map_metadata", "-1", saida.toAbsolutePath().toString()));
    }

    List<String> comandoHls(String entrada, Path pasta, int altura) {
        List<String> a = base(entrada);
        a.addAll(List.of("-map", "0:v:0", "-map", "0:a:0?", "-vf", "scale=-2:" + altura,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-map_metadata", "-1", "-map_chapters", "-1",
                "-f", "hls", "-hls_time", "4", "-hls_playlist_type", "vod",
                "-hls_segment_filename", pasta.resolve(altura + "p_%03d.ts").toAbsolutePath().toString(),
                pasta.resolve(altura + "p.m3u8").toAbsolutePath().toString()));
        return a;
    }

    private List<String> base(String entrada) {
        return new ArrayList<>(List.of(ffmpeg, "-y", "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "2", "-i", entrada));
    }

    private void rodar(List<String> args) throws IOException {
        ExecutorDeComando.Saida s = executor.executar(args, limite);
        if (s.codigo() != 0) {
            String resumo = s.texto() == null ? "" : s.texto().strip();
            throw new IOException("FFmpeg falhou (código " + s.codigo() + "): "
                    + (resumo.length() > 200 ? resumo.substring(0, 200) : resumo));
        }
    }

    @Override
    public String descricao() {
        return "FFmpeg (H.264+AAC, HLS 360/720/1080, poster, sem metadados)";
    }

    @Override
    public boolean aceitaWebm() {
        return true;
    }
}
