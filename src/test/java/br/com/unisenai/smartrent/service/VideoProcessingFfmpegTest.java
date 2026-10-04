package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.VideoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pipeline com FFmpeg, testado com um executor falso: confere o que o servico
 * pede ao FFmpeg (H.264+AAC, faststart, 1080p no maximo, sem metadados, HLS,
 * poster) e como reage a erros, sem depender do binario instalado.
 */
class VideoProcessingFfmpegTest {

    /** Executor que registra as chamadas, cria os arquivos de saida e responde ao ffprobe com um JSON. */
    static class ExecutorFalso implements ExecutorDeComando {
        final List<List<String>> chamadas = new ArrayList<>();
        String jsonFfprobe;
        int codigoFfmpeg = 0;
        boolean estourarTempo;

        @Override
        public Saida executar(List<String> args, Duration limite) throws IOException {
            chamadas.add(args);
            if (estourarTempo) {
                throw new IOException("Tempo limite de " + limite.toSeconds() + " s excedido.");
            }
            if (args.get(0).endsWith("ffprobe")) {
                return new Saida(jsonFfprobe == null ? 1 : 0, jsonFfprobe == null ? "invalid data" : jsonFfprobe);
            }
            if (codigoFfmpeg == 0) { // o ultimo argumento do ffmpeg e o arquivo de saida
                Path saida = Path.of(args.get(args.size() - 1));
                Files.createDirectories(saida.getParent());
                Files.write(saida, "saida".getBytes(StandardCharsets.UTF_8));
            }
            return new Saida(codigoFfmpeg, "erro simulado");
        }
    }

    @TempDir
    Path pasta;

    private ExecutorFalso executor;
    private VideoProcessingFfmpeg ffmpeg;
    private Path entrada;

    private static String json(double duracao, int largura, int altura, String formato) {
        return "{\"streams\":[{\"codec_type\":\"audio\"},{\"codec_type\":\"video\",\"width\":" + largura + ",\"height\":" + altura
                + "}],\"format\":{\"format_name\":\"" + formato + "\",\"duration\":\"" + duracao + "\"}}";
    }

    @BeforeEach
    void preparar() throws IOException {
        executor = new ExecutorFalso();
        executor.jsonFfprobe = json(45.5, 1920, 1080, "mov,mp4,m4a,3gp,3g2,mj2");
        ffmpeg = new VideoProcessingFfmpeg("ffmpeg", "ffprobe", executor, VideoProperties.padrao());
        entrada = pasta.resolve("original.mp4");
        Files.write(entrada, new byte[]{1, 2, 3});
    }

    private List<List<String>> comandosFfmpeg() {
        return executor.chamadas.stream().filter(c -> c.get(0).equals("ffmpeg")).toList();
    }

    @Test
    @DisplayName("CT700 - ffprobe: duracao, resolucao e formato vem do proprio arquivo")
    void inspecao() {
        var meta = ffmpeg.inspecionar(entrada);
        assertEquals(45.5, meta.duracaoSegundos(), 0.001);
        assertEquals(1920, meta.largura());
        assertEquals(1080, meta.altura());
        assertTrue(meta.formato().contains("mp4"));
    }

    @Test
    @DisplayName("CT701 - Sem trilha de video, arquivo ilegivel ou formato nao aceito sao rejeitados com mensagem clara")
    void inspecaoRejeita() {
        executor.jsonFfprobe = "{\"streams\":[{\"codec_type\":\"audio\"}],\"format\":{\"format_name\":\"mp3\",\"duration\":\"10\"}}";
        assertEquals("O arquivo não tem trilha de vídeo.",
                assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> ffmpeg.inspecionar(entrada)).getMessage());

        executor.jsonFfprobe = null; // ffprobe falha (codigo 1)
        assertTrue(assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> ffmpeg.inspecionar(entrada))
                .getMessage().contains("não é um vídeo válido"));

        executor.jsonFfprobe = json(30, 640, 480, "avi");
        assertTrue(assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> ffmpeg.inspecionar(entrada))
                .getMessage().contains("Formato de vídeo não aceito"));
    }

    @Test
    @DisplayName("CT702 - Processar gera MP4 H.264+AAC com faststart, ate 1080p e sem metadados (GPS incluso)")
    void mp4() throws IOException {
        var r = ffmpeg.processar(entrada, pasta.resolve("saida"));

        List<String> cmd = comandosFfmpeg().stream().filter(c -> c.get(c.size() - 1).endsWith("video.mp4")).findFirst().orElseThrow();
        assertTrue(cmd.containsAll(List.of("libx264", "aac", "+faststart", "yuv420p")));
        assertEquals("-1", cmd.get(cmd.indexOf("-map_metadata") + 1), "metadados (inclusive GPS) removidos");
        assertTrue(cmd.contains("scale=-2:'min(1080,ih)'"), "reescala para no maximo 1080p");
        assertTrue(Files.exists(r.mp4()));
        assertEquals(45.5, r.metadados().duracaoSegundos(), 0.001);
    }

    @Test
    @DisplayName("CT703 - Gera poster (sem metadados) e HLS com 360p, 720p e 1080p quando a origem e 1080p; lista mestre completa")
    void hlsEPoster() throws IOException {
        var r = ffmpeg.processar(entrada, pasta.resolve("saida"));

        assertTrue(Files.exists(r.poster()));
        List<String> cmdPoster = comandosFfmpeg().stream().filter(c -> c.get(c.size() - 1).endsWith("poster.jpg")).findFirst().orElseThrow();
        assertEquals("-1", cmdPoster.get(cmdPoster.indexOf("-map_metadata") + 1));

        String mestre = Files.readString(r.hlsMestre());
        assertTrue(mestre.startsWith("#EXTM3U"));
        for (String p : List.of("360p.m3u8", "720p.m3u8", "1080p.m3u8")) {
            assertTrue(mestre.contains(p), p);
        }
        assertTrue(mestre.contains("RESOLUTION=1920x1080") && mestre.contains("RESOLUTION=640x360"));
        long hls = comandosFfmpeg().stream().filter(c -> c.contains("hls")).count();
        assertEquals(3, hls);
        assertTrue(comandosFfmpeg().stream().filter(c -> c.contains("hls")).allMatch(c -> c.contains("-map_metadata")));
    }

    @Test
    @DisplayName("CT704 - Origem 720p nao gera variante 1080p (nao amplia); origem pequena gera ao menos 360p")
    void escadaLimitadaPelaOrigem() throws IOException {
        executor.jsonFfprobe = json(30, 1280, 720, "mov,mp4");
        var r = ffmpeg.processar(entrada, pasta.resolve("s720"));
        String mestre = Files.readString(r.hlsMestre());
        assertTrue(mestre.contains("720p.m3u8") && !mestre.contains("1080p.m3u8"));

        assertEquals(List.of(360), VideoProcessingFfmpeg.alturasPara(240));
        assertEquals(List.of(360, 720, 1080), VideoProcessingFfmpeg.alturasPara(2160));
    }

    @Test
    @DisplayName("CT705 - Nenhum argumento e montado a partir de nome ou metadado do usuario: tudo e lista, sem shell")
    void semShellNemEntradaDoUsuario() throws IOException {
        Path perigoso = pasta.resolve("a; rm -rf $(whoami) `x`.mp4");
        Files.write(perigoso, new byte[]{1});
        ffmpeg.processar(perigoso, pasta.resolve("s"));

        for (List<String> c : executor.chamadas) {
            assertFalse(c.get(0).contains(" "), "o programa e um token unico");
            assertTrue(c.stream().noneMatch(a -> a.equals("sh") || a.equals("cmd") || a.equals("-c")), "sem shell");
        }
        // o nome estranho vai como UM argumento intacto (nao interpretado), nunca concatenado a um comando
        assertTrue(executor.chamadas.stream().anyMatch(c -> c.contains(perigoso.toAbsolutePath().toString())));
    }

    @Test
    @DisplayName("CT706 - Erro do FFmpeg e tempo esgotado viram IOException (o worker faz retry); processar de novo sobrescreve")
    void falhasEIdempotencia() throws IOException {
        executor.codigoFfmpeg = 1;
        var erro = assertThrows(IOException.class, () -> ffmpeg.processar(entrada, pasta.resolve("f")));
        assertTrue(erro.getMessage().contains("FFmpeg falhou"));

        executor.codigoFfmpeg = 0;
        executor.estourarTempo = true;
        assertThrows(Exception.class, () -> ffmpeg.processar(entrada, pasta.resolve("f")));

        executor.estourarTempo = false;
        var a = ffmpeg.processar(entrada, pasta.resolve("g"));
        var b = ffmpeg.processar(entrada, pasta.resolve("g")); // repetir sobrescreve os mesmos arquivos
        assertEquals(a.mp4(), b.mp4());
        assertEquals(a.hlsMestre(), b.hlsMestre());
    }

    @Test
    @DisplayName("CT707 - Sem FFmpeg o processamento basico valida o MP4 pelo cabecalho, publica o proprio arquivo e nao aceita WebM")
    void processamentoBasico() throws IOException {
        var basico = new VideoProcessingBasico();
        Path mp4 = pasta.resolve("v.mp4");
        Files.write(mp4, Mp4Falso.comDuracao(60, 100));

        assertEquals(60.0, basico.inspecionar(mp4).duracaoSegundos(), 0.001);
        var r = basico.processar(mp4, pasta.resolve("b"));
        assertTrue(Files.exists(r.mp4()));
        assertNull(r.poster());
        assertNull(r.hlsMestre());
        assertFalse(basico.aceitaWebm());
        assertTrue(ffmpeg.aceitaWebm());
        assertThrows(VideoProcessingService.VideoInvalidoException.class, () -> basico.inspecionar(entrada));
    }
}
