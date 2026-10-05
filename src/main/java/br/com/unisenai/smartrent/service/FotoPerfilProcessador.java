package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Reprocessa a foto de perfil no servidor, so com o JDK ({@code javax.imageio}):
 * <ol>
 *   <li>confere o tipo REAL pelos primeiros bytes (PNG ou JPEG), sem confiar em extensao nem em Content-Type;</li>
 *   <li>confere tamanho do arquivo e dimensoes lendo so o cabecalho, ANTES de decodificar
 *       (um arquivo pequeno que declara bilhoes de pixels nao chega a gastar memoria);</li>
 *   <li>decodifica, aplica a orientacao EXIF, recorta um quadrado centralizado e reduz;</li>
 *   <li>recodifica sempre em JPEG, sem metadados (EXIF/GPS) e sobre fundo branco (PNG transparente),
 *       com as pecas comuns em {@link ImagemSegura}.</li>
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
        String formato = ImagemSegura.formatoPelosBytes(original);
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
            imagem = ImagemSegura.orientar(imagem, ImagemSegura.orientacaoExif(original));
        }
        BufferedImage quadrada = quadradoReduzido(imagem);
        return ImagemSegura.codificarJpeg(quadrada, props.fotoQualidade());
    }

    // ----------------------------------------------------------------- tipo real

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
}
