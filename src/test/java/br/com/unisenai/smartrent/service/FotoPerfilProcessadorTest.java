package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** Foto de perfil: tipo real, limites, reprocessamento e remocao de metadados. */
class FotoPerfilProcessadorTest {

    private final FotoPerfilProcessador processador = new FotoPerfilProcessador(PerfilProperties.padrao());

    private static byte[] imagem(String formato, int largura, int altura, int tipo) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, tipo);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, largura / 2, altura);
        g.setColor(Color.BLUE);
        g.fillRect(largura / 2, 0, largura - largura / 2, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, formato, saida));
        return saida.toByteArray();
    }

    private static BufferedImage ler(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private void recusa(byte[] arquivo, String trecho) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> processador.processar(arquivo));
        assertTrue(e.getMessage().contains(trecho), "mensagem inesperada: " + e.getMessage());
    }

    @Test
    @DisplayName("CT447 - Aceita PNG e JPG, recorta quadrado ao centro, reduz para 512x512 e devolve JPEG")
    void aceitaPngEJpg() throws IOException {
        byte[] jpegOriginal = imagem("jpg", 1200, 800, BufferedImage.TYPE_INT_RGB);
        byte[] pngOriginal = imagem("png", 900, 1300, BufferedImage.TYPE_INT_ARGB);

        for (byte[] original : new byte[][]{jpegOriginal, pngOriginal}) {
            byte[] saida = processador.processar(original);
            assertEquals((byte) 0xFF, saida[0]);
            assertEquals((byte) 0xD8, saida[1]);
            BufferedImage img = ler(saida);
            assertEquals(512, img.getWidth());
            assertEquals(512, img.getHeight());
        }
    }

    @Test
    @DisplayName("CT448 - Nao amplia imagens pequenas: foto 200x300 vira 200x200")
    void naoAmplia() throws IOException {
        BufferedImage img = ler(processador.processar(imagem("png", 200, 300, BufferedImage.TYPE_INT_RGB)));
        assertEquals(200, img.getWidth());
        assertEquals(200, img.getHeight());
    }

    @Test
    @DisplayName("CT449 - Rejeita SVG, GIF, WebP e texto com extensao falsa: so vale o tipo real pelos bytes")
    void rejeitaOutrosTipos() throws IOException {
        recusa("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"300\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8), "PNG ou JPG");
        recusa(imagem("gif", 300, 300, BufferedImage.TYPE_INT_RGB), "PNG ou JPG");
        byte[] webp = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 8);
        recusa(webp, "PNG ou JPG");
        recusa("isto nao e uma imagem, so foi chamado de foto.png".getBytes(StandardCharsets.UTF_8), "PNG ou JPG");
        recusa(new byte[0], "Escolha");
    }

    @Test
    @DisplayName("CT450 - Rejeita arquivo acima do limite de tamanho e imagem abaixo do minimo")
    void rejeitaTamanhoEMinimo() throws IOException {
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        byte[] cabecalhoPng = imagem("png", 300, 300, BufferedImage.TYPE_INT_RGB);
        System.arraycopy(cabecalhoPng, 0, grande, 0, 16);
        recusa(grande, "até 2 MB");

        recusa(imagem("png", 100, 100, BufferedImage.TYPE_INT_RGB), "128");
        recusa(imagem("jpg", 500, 120, BufferedImage.TYPE_INT_RGB), "128");
    }

    /** PNG minusculo (menos de 100 bytes) que DECLARA 10000x10000 pixels: bomba de descompressao. */
    private static byte[] pngQueDeclaraPixelsDemais(int largura, int altura) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.write(new byte[]{'I', 'H', 'D', 'R'});
        ihdr.write(inteiro(largura));
        ihdr.write(inteiro(altura));
        ihdr.write(new byte[]{8, 2, 0, 0, 0});
        saida.write(inteiro(13));
        saida.write(ihdr.toByteArray());
        CRC32 crc = new CRC32();
        crc.update(ihdr.toByteArray());
        saida.write(inteiro((int) crc.getValue()));
        saida.write(new byte[]{0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82});
        return saida.toByteArray();
    }

    private static byte[] inteiro(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    @Test
    @DisplayName("CT451 - Rejeita imagem com pixels demais lendo so o cabecalho (anti decompression bomb)")
    void rejeitaBombaDePixels() throws IOException {
        byte[] bomba = pngQueDeclaraPixelsDemais(10_000, 10_000);
        assertTrue(bomba.length < 100);
        recusa(bomba, "megapixels");
    }

    /** Insere um bloco EXIF (orientacao + texto "GPS") logo depois do SOI de um JPEG. */
    private static byte[] comExif(byte[] jpeg, int orientacao) {
        byte[] tiff = {'I', 'I', 0x2A, 0, 8, 0, 0, 0,
                1, 0, // uma entrada
                0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientacao, 0, 0, 0,
                0, 0, 0, 0};
        byte[] gps = "GPSLatitude=-27.59".getBytes(StandardCharsets.US_ASCII);
        byte[] dados = new byte[6 + tiff.length + gps.length];
        System.arraycopy(new byte[]{'E', 'x', 'i', 'f', 0, 0}, 0, dados, 0, 6);
        System.arraycopy(tiff, 0, dados, 6, tiff.length);
        System.arraycopy(gps, 0, dados, 6 + tiff.length, gps.length);
        int tamanho = dados.length + 2;
        byte[] saida = new byte[jpeg.length + dados.length + 4];
        System.arraycopy(jpeg, 0, saida, 0, 2);
        saida[2] = (byte) 0xFF;
        saida[3] = (byte) 0xE1;
        saida[4] = (byte) (tamanho >> 8);
        saida[5] = (byte) tamanho;
        System.arraycopy(dados, 0, saida, 6, dados.length);
        System.arraycopy(jpeg, 2, saida, 6 + dados.length, jpeg.length - 2);
        return saida;
    }

    private static boolean contem(byte[] arquivo, String texto) {
        return new String(arquivo, StandardCharsets.ISO_8859_1).contains(texto);
    }

    @Test
    @DisplayName("CT452 - Remove EXIF/GPS da saida e respeita a orientacao antes de recortar")
    void removeMetadadosEOrientacao() throws IOException {
        // Largo (400x200): esquerda vermelha, direita azul. Orientacao 6 = girar 90 graus no sentido horario:
        // depois de girar, o vermelho fica em cima e o azul embaixo.
        byte[] original = comExif(imagem("jpg", 400, 200, BufferedImage.TYPE_INT_RGB), 6);
        assertTrue(contem(original, "Exif") && contem(original, "GPSLatitude"));

        byte[] saida = processador.processar(original);
        assertFalse(contem(saida, "Exif"), "EXIF nao pode sobreviver");
        assertFalse(contem(saida, "GPS"), "GPS nao pode sobreviver");

        BufferedImage img = ler(saida);
        assertEquals(200, img.getWidth());
        assertEquals(200, img.getHeight());
        Color topo = new Color(img.getRGB(100, 20));
        Color base = new Color(img.getRGB(100, 180));
        assertTrue(topo.getRed() > 150 && topo.getBlue() < 100, "topo deveria ser vermelho: " + topo);
        assertTrue(base.getBlue() > 150 && base.getRed() < 100, "base deveria ser azul: " + base);
    }

    @Test
    @DisplayName("CT453 - PNG com transparencia fica sobre fundo branco (JPEG nao tem alfa)")
    void transparenciaViraBranco() throws IOException {
        BufferedImage transparente = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB); // todo transparente
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(transparente, "png", saida);
        BufferedImage img = ler(processador.processar(saida.toByteArray()));
        Color c = new Color(img.getRGB(10, 10));
        assertTrue(c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240);
    }
}
