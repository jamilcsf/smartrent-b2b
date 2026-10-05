package br.com.unisenai.smartrent.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Pecas comuns do reprocessamento de imagens enviadas (foto de perfil e fotos de anuncio), so com o JDK:
 * deteccao do tipo real pelos bytes, leitura e aplicacao da orientacao EXIF e recodificacao SEM metadados
 * (EXIF, GPS, XMP, miniatura embutida e comentarios nao sobrevivem, pois o escritor recebe metadados nulos).
 */
final class ImagemSegura {

    private ImagemSegura() {
    }

    /** "png", "jpeg" ou nulo, pelos primeiros bytes (nunca pela extensao nem pelo Content-Type). */
    static String formatoPelosBytes(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        return null;
    }

    /** Orientacao EXIF (1 a 8) do JPEG; 1 quando nao ha ou o bloco e invalido. */
    static int orientacaoExif(byte[] b) {
        try {
            int i = 2; // depois do SOI
            while (i + 4 < b.length && (b[i] & 0xFF) == 0xFF) {
                int marcador = b[i + 1] & 0xFF;
                if (marcador == 0xDA || marcador == 0xD9) {
                    break; // dados da imagem: acabaram os blocos de metadados
                }
                int tamanho = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (marcador == 0xE1 && tamanho >= 16 && b[i + 4] == 'E' && b[i + 5] == 'x'
                        && b[i + 6] == 'i' && b[i + 7] == 'f') {
                    return orientacaoDoTiff(b, i + 10, i + 2 + tamanho);
                }
                i += 2 + tamanho;
            }
        } catch (RuntimeException e) {
            // bloco malformado: segue sem rotacionar
        }
        return 1;
    }

    private static int orientacaoDoTiff(byte[] b, int inicio, int fim) {
        boolean little = b[inicio] == 'I';
        int ifd = inicio + inteiro(b, inicio + 4, 4, little);
        if (ifd + 2 > fim) {
            return 1;
        }
        int entradas = inteiro(b, ifd, 2, little);
        for (int e = 0; e < entradas; e++) {
            int pos = ifd + 2 + e * 12;
            if (pos + 12 > fim) {
                break;
            }
            if (inteiro(b, pos, 2, little) == 0x0112) {
                int valor = inteiro(b, pos + 8, 2, little);
                return valor >= 1 && valor <= 8 ? valor : 1;
            }
        }
        return 1;
    }

    private static int inteiro(byte[] b, int pos, int bytes, boolean little) {
        int v = 0;
        for (int k = 0; k < bytes; k++) {
            int octeto = b[pos + (little ? bytes - 1 - k : k)] & 0xFF;
            v = (v << 8) | octeto;
        }
        return v;
    }

    /** True quando a orientacao troca largura e altura (5 a 8). */
    static boolean trocaEixos(int orientacao) {
        return orientacao >= 5 && orientacao <= 8;
    }

    /** Aplica a orientacao EXIF aos pixels (resultado RGB, fundo branco sob transparencia). */
    static BufferedImage orientar(BufferedImage img, int orientacao) {
        if (orientacao <= 1) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t;
        boolean troca = trocaEixos(orientacao);
        switch (orientacao) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> t = new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);
            default -> t = new AffineTransform(0, -1, 1, 0, 0, w);
        }
        BufferedImage saida = new BufferedImage(troca ? h : w, troca ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, saida.getWidth(), saida.getHeight());
            g.drawImage(img, t, null);
        } finally {
            g.dispose();
        }
        return saida;
    }

    /** JPEG sem metadados (so o minimo JFIF). Imagem com transparencia vira RGB sobre branco. */
    static byte[] codificarJpeg(BufferedImage imagem, float qualidade) {
        BufferedImage rgb = imagem;
        if (imagem.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(imagem, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(qualidade);
            escritor.setOutput(fluxo);
            // Metadados nulos: o escritor grava so o minimo (JFIF), sem EXIF, GPS, XMP ou miniatura.
            escritor.write(null, new IIOImage(rgb, null, null), parametros);
            fluxo.flush();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a imagem.", e);
        } finally {
            escritor.dispose();
        }
    }

    /** PNG sem blocos auxiliares (eXIf, tEXt, iTXt...): so cabecalho, paleta e dados. Preserva a transparencia. */
    static byte[] codificarPng(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("png").next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            escritor.setOutput(fluxo);
            escritor.write(null, new IIOImage(imagem, null, null), escritor.getDefaultWriteParam());
            fluxo.flush();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a imagem.", e);
        } finally {
            escritor.dispose();
        }
    }
}
