package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Validacao de imagem (tipo real, 2:1 das 360, tamanho) e de video (duracao lida no servidor). */
class MidiaProcessadorTest {

    @TempDir
    Path pasta;

    private MidiaProcessador processador;

    @BeforeEach
    void preparar() {
        processador = new MidiaProcessador(new MidiaProperties(pasta.toString(), 10, 100));
    }

    private Path imagem(String nome, String formato, int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Path destino = pasta.resolve(nome);
        ImageIO.write(img, formato, destino.toFile());
        return destino;
    }

    // ------------------------------------------------------------ imagens

    @Test
    @DisplayName("CT70 - Foto 360 com proporcao 2:1 e aceita")
    void panoramica2x1Aceita() throws IOException {
        var insp = processador.inspecionarImagem(imagem("p.jpg", "jpg", 400, 200), "image/jpeg", true);
        assertEquals(400, insp.largura());
        assertEquals(200, insp.altura());
        assertEquals("jpg", insp.extensao());
    }

    @Test
    @DisplayName("CT71 - Foto 360 fora de 2:1 e recusada, mas a mesma imagem vale como foto comum")
    void panoramicaForaDe2x1Recusada() throws IOException {
        Path img = imagem("n.jpg", "jpg", 300, 200); // 3:2
        var erro = assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarImagem(img, "image/jpeg", true));
        assertTrue(erro.getMessage().contains("2:1"));

        assertDoesNotThrow(() -> processador.inspecionarImagem(img, "image/jpeg", false));
    }

    @Test
    @DisplayName("CT72 - Tipo e conferido pelo conteudo: texto declarado como JPEG e recusado")
    void conteudoNaoImagem() throws IOException {
        Path falso = pasta.resolve("falso.jpg");
        Files.writeString(falso, "isto nao e uma imagem de verdade");
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarImagem(falso, "image/jpeg", false));
    }

    @Test
    @DisplayName("CT73 - MIME declarado diferente do conteudo real e recusado")
    void mimeDivergente() throws IOException {
        Path png = imagem("a.png", "png", 100, 100);
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarImagem(png, "image/jpeg", false));
        assertDoesNotThrow(() -> processador.inspecionarImagem(png, "image/png", false));
    }

    @Test
    @DisplayName("CT74 - Formatos fora de JPEG/PNG (ex.: GIF) sao recusados")
    void formatoNaoPermitido() throws IOException {
        Path gif = imagem("a.gif", "gif", 50, 50);
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarImagem(gif, "image/gif", false));
    }

    @Test
    @DisplayName("CT75 - Imagem acima do tamanho maximo e recusada")
    void imagemGrandeDemais() throws IOException {
        MidiaProcessador pequeno = new MidiaProcessador(new MidiaProperties(pasta.toString(), 0, 100));
        Path img = imagem("g.jpg", "jpg", 100, 100);
        assertThrows(ValidacaoAnuncioException.class, () -> pequeno.inspecionarImagem(img, "image/jpeg", false));
    }

    @Test
    @DisplayName("CT76 - Miniatura e gerada em JPEG com ate 480 px de largura")
    void miniatura() throws IOException {
        Path origem = imagem("grande.png", "png", 1200, 600);
        Path destino = pasta.resolve("mini.jpg");

        assertTrue(processador.gerarMiniatura(origem, destino));

        BufferedImage mini = ImageIO.read(destino.toFile());
        assertEquals(480, mini.getWidth());
        assertEquals(240, mini.getHeight());
    }

    // -------------------------------------------------------------- videos

    /** MP4 minimo: ftyp + moov(mvhd). So o cabecalho importa para a duracao. */
    private Path mp4(String nome, long escala, long duracao, boolean comMoov) throws IOException {
        ByteBuffer ftyp = ByteBuffer.allocate(24);
        ftyp.putInt(24).put("ftyp".getBytes(StandardCharsets.ISO_8859_1))
                .put("isom".getBytes(StandardCharsets.ISO_8859_1)).putInt(0)
                .put("isom".getBytes(StandardCharsets.ISO_8859_1)).put("mp41".getBytes(StandardCharsets.ISO_8859_1));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(ftyp.array());
        if (comMoov) {
            ByteBuffer mvhd = ByteBuffer.allocate(108);
            mvhd.putInt(108).put("mvhd".getBytes(StandardCharsets.ISO_8859_1));
            mvhd.putInt(0);              // versao + flags
            mvhd.putInt(0).putInt(0);    // criacao, modificacao
            mvhd.putInt((int) escala).putInt((int) duracao);
            ByteBuffer moov = ByteBuffer.allocate(8 + 108);
            moov.putInt(8 + 108).put("moov".getBytes(StandardCharsets.ISO_8859_1)).put(mvhd.array());
            saida.write(moov.array());
        }
        saida.write(new byte[64]); // "mdat" ficticio depois do moov: o parser nao deve se perder
        Path destino = pasta.resolve(nome);
        Files.write(destino, saida.toByteArray());
        return destino;
    }

    @Test
    @DisplayName("CT77 - Video de ate 1:30 e aceito; a duracao e lida do cabecalho")
    void videoDentroDoLimite() throws IOException {
        var insp = processador.inspecionarVideo(mp4("v.mp4", 1000, 60_000, true), "video/mp4", 90);
        assertEquals(60, insp.duracaoSegundos());
        assertEquals("mp4", insp.extensao());
    }

    @Test
    @DisplayName("CT78 - Video com exatamente 1:30 (90 s) e aceito")
    void videoNoLimiteExato() throws IOException {
        assertDoesNotThrow(() -> processador.inspecionarVideo(mp4("v.mp4", 1000, 90_000, true), "video/mp4", 90));
    }

    @Test
    @DisplayName("CT79 - Video acima de 1:30 (91 s) e recusado pelo servidor, com a duracao e o limite na mensagem")
    void videoLongoDemais() throws IOException {
        var erro = assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarVideo(mp4("v.mp4", 1000, 91_000, true), "video/mp4", 90));
        assertEquals("O vídeo tem 1:31; o máximo permitido é 1:30.", erro.getMessage());
    }

    @Test
    @DisplayName("CT80 - Video cuja duracao nao pode ser lida e recusado")
    void videoSemCabecalhoLegivel() throws IOException {
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarVideo(mp4("v.mp4", 1000, 10_000, false), "video/mp4", 90));
    }

    @Test
    @DisplayName("CT81 - Arquivo que nao e MP4 e recusado mesmo declarado como video/mp4")
    void videoFalso() throws IOException {
        Path falso = pasta.resolve("f.mp4");
        Files.writeString(falso, "nao sou um video, mas tenho mais de oito bytes");
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarVideo(falso, "video/mp4", 90));
    }

    @Test
    @DisplayName("CT82 - Tipos de video fora de MP4/MOV sao recusados")
    void videoTipoNaoPermitido() throws IOException {
        assertThrows(ValidacaoAnuncioException.class,
                () -> processador.inspecionarVideo(mp4("v.avi", 1000, 10_000, true), "video/x-msvideo", 90));
    }
}
