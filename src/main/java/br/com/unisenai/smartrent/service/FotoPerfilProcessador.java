package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Reprocessa a foto de perfil no servidor, so com o JDK ({@code javax.imageio}):
 * <ol>
 *   <li>confere o tipo REAL pelos primeiros bytes (PNG ou JPEG), sem confiar em extensao nem em Content-Type;</li>
 *   <li>confere tamanho do arquivo e dimensoes lendo so o cabecalho, ANTES de decodificar
 *       (um arquivo pequeno que declara bilhoes de pixels nao chega a gastar memoria);</li>
 *   <li>decodifica, aplica a orientacao EXIF, recorta um quadrado centralizado e reduz;</li>
 *   <li>recodifica sempre em JPEG, sem metadados (EXIF/GPS) e sobre fundo branco (PNG transparente).</li>
 * </ol>
 * A imagem original nunca e guardada: so o resultado deste metodo.
 */
@Component
public class FotoPerfilProcessador {

    private static final String MSG_TIPO = "Use uma imagem PNG ou JPG de até %d MB.";

    private final PerfilProperties props;

    public FotoPerfilProcessador(PerfilProperties props) {
        this.props = props;
    }

    /** Devolve o JPEG final; lanca {@link IllegalArgumentException} com mensagem para o usuario. */
    public byte[] processar(byte[] original) {
        if (original == null || original.length == 0) {
            throw new IllegalArgumentException("Escolha uma imagem PNG ou JPG.");
        }
        if (original.length > props.fotoMaxMb() * 1024L * 1024L) {
            throw new IllegalArgumentException(String.format(MSG_TIPO, props.fotoMaxMb()));
        }
        String formato = formatoPelosBytes(original);
        if (formato == null) {
            throw new IllegalArgumentException(String.format(MSG_TIPO, props.fotoMaxMb()));
        }

        BufferedImage imagem;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            ImageReader leitor = leitorDo(in, formato);
            try {
                leitor.setInput(in, true, true);
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura < props.fotoMinLado() || altura < props.fotoMinLado()) {
                    throw new IllegalArgumentException("A imagem deve ter pelo menos "
                            + props.fotoMinLado() + "×" + props.fotoMinLado() + " pixels.");
                }
                if ((long) largura * altura > props.fotoMaxPixels()) {
                    throw new IllegalArgumentException("A imagem tem dimensões grandes demais (máximo de "
                            + props.fotoMaxPixels() / 1_000_000L + " megapixels). Reduza e tente de novo.");
                }
                imagem = leitor.read(0);
            } finally {
                leitor.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Não foi possível ler a imagem. Use um arquivo PNG ou JPG válido.");
        }
        if (imagem == null) {
            throw new IllegalArgumentException("Não foi possível ler a imagem. Use um arquivo PNG ou JPG válido.");
        }

        if ("jpeg".equals(formato)) {
            imagem = orientar(imagem, orientacaoExif(original));
        }
        BufferedImage quadrada = quadradoReduzido(imagem);
        return codificarJpeg(quadrada);
    }

    // ----------------------------------------------------------------- tipo real

    /** "png", "jpeg" ou nulo, pelos primeiros bytes. */
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

    private static ImageReader leitorDo(ImageInputStream in, String formato) throws IOException {
        Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
        while (leitores.hasNext()) {
            ImageReader r = leitores.next();
            if (formato.equalsIgnoreCase(r.getFormatName())) {
                return r;
            }
            r.dispose();
        }
        throw new IOException("sem leitor para " + formato);
    }

    // ------------------------------------------------------------------ imagem

    private BufferedImage quadradoReduzido(BufferedImage origem) {
        int lado = Math.min(origem.getWidth(), origem.getHeight());
        int x = (origem.getWidth() - lado) / 2;
        int y = (origem.getHeight() - lado) / 2;
        BufferedImage corte = origem.getSubimage(x, y, lado, lado);
        int alvo = Math.min(props.fotoLado(), lado); // nunca amplia (nao inventa pixels)

        // Reducao em etapas (metade por vez) preserva nitidez em fotos grandes.
        BufferedImage atual = sobreFundoBranco(corte, lado, lado);
        while (atual.getWidth() >= alvo * 2) {
            atual = sobreFundoBranco(atual, atual.getWidth() / 2, atual.getHeight() / 2);
        }
        return atual.getWidth() == alvo ? atual : sobreFundoBranco(atual, alvo, alvo);
    }

    private static BufferedImage sobreFundoBranco(BufferedImage origem, int largura, int altura) {
        BufferedImage saida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largura, altura);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(origem, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }
        return saida;
    }

    private byte[] codificarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(props.fotoQualidade());
            escritor.setOutput(fluxo);
            // Metadados nulos: o escritor grava so o minimo (JFIF), sem EXIF, GPS ou miniatura.
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
            fluxo.flush();
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a foto de perfil.", e);
        } finally {
            escritor.dispose();
        }
    }

    // ------------------------------------------------------------- orientacao

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

    private static BufferedImage orientar(BufferedImage img, int orientacao) {
        if (orientacao <= 1) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t;
        boolean troca = false;
        switch (orientacao) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> { t = new AffineTransform(0, 1, 1, 0, 0, 0); troca = true; }
            case 6 -> { t = new AffineTransform(0, 1, -1, 0, h, 0); troca = true; }
            case 7 -> { t = new AffineTransform(0, -1, -1, 0, h, w); troca = true; }
            default -> { t = new AffineTransform(0, -1, 1, 0, 0, w); troca = true; }
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
}
