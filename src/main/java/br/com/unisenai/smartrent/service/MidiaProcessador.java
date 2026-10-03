package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Inspeciona o arquivo enviado e gera miniaturas.
 *
 * <p>Nada do que o navegador declara e aceito como prova: o tipo e conferido
 * pelos primeiros bytes do arquivo, as dimensoes sao lidas da propria imagem e
 * a duracao do video e lida do cabecalho do MP4.
 */
@Component
public class MidiaProcessador {

    /** Teto de pixels: protege a memoria do servidor contra imagens-bomba. */
    private static final long MAX_PIXELS = 60_000_000L;
    private static final double TOLERANCIA_360 = 0.02;
    private static final int LARGURA_MINIATURA = 480;
    private static final Set<String> MIMES_IMAGEM = Set.of("image/jpeg", "image/png");
    private static final Set<String> MIMES_VIDEO = Set.of("video/mp4", "video/quicktime");

    private final long maxBytesImagem;
    private final long maxBytesVideo;

    public MidiaProcessador(MidiaProperties props) {
        this.maxBytesImagem = props.tamanhoMaxImagemMb() * 1024 * 1024;
        this.maxBytesVideo = props.tamanhoMaxVideoMb() * 1024 * 1024;
    }

    /** Resultado da inspecao. {@code extensao} vai no nome do arquivo guardado. */
    public record Inspecao(String mime, String extensao, Integer largura, Integer altura,
                           Integer duracaoSegundos) {
    }

    public Inspecao inspecionarImagem(Path arquivo, String mimeDeclarado, boolean panoramica) {
        try {
            long tamanho = Files.size(arquivo);
            if (tamanho > maxBytesImagem) {
                throw new ValidacaoAnuncioException("A imagem excede o tamanho máximo de "
                        + (maxBytesImagem / 1024 / 1024) + " MB.");
            }
            String real = detectarImagem(arquivo);
            if (real == null || !MIMES_IMAGEM.contains(normalizar(mimeDeclarado)) || !real.equals(normalizar(mimeDeclarado))) {
                throw new ValidacaoAnuncioException("Formato de imagem não aceito. Envie JPEG ou PNG.");
            }
            int[] dim = dimensoes(arquivo);
            if ((long) dim[0] * dim[1] > MAX_PIXELS) {
                throw new ValidacaoAnuncioException("A resolução da imagem é alta demais (máximo 60 megapixels).");
            }
            if (panoramica) {
                if (!proporcao360(dim[0], dim[1])) {
                    throw new ValidacaoAnuncioException(
                            "Foto 360 deve ter proporção 2:1 (imagem equirretangular). Esta tem "
                                    + dim[0] + "x" + dim[1] + ".");
                }
            }
            return new Inspecao(real, real.equals("image/png") ? "png" : "jpg", dim[0], dim[1], null);
        } catch (IOException e) {
            throw new ValidacaoAnuncioException("Não foi possível ler a imagem enviada.");
        }
    }

    /** Foto 360 equirretangular: largura = 2 x altura, com 2% de tolerancia. */
    public static boolean proporcao360(Integer largura, Integer altura) {
        if (largura == null || altura == null || altura == 0) {
            return false;
        }
        return Math.abs((double) largura / altura - 2.0) <= 2.0 * TOLERANCIA_360;
    }

    public Inspecao inspecionarVideo(Path arquivo, String mimeDeclarado, int duracaoMaximaSegundos) {
        try {
            long tamanho = Files.size(arquivo);
            if (tamanho > maxBytesVideo) {
                throw new ValidacaoAnuncioException("O vídeo excede o tamanho máximo de "
                        + (maxBytesVideo / 1024 / 1024) + " MB.");
            }
            if (!MIMES_VIDEO.contains(normalizar(mimeDeclarado)) || !pareceMp4(arquivo)) {
                throw new ValidacaoAnuncioException("Formato de vídeo não aceito. Envie um arquivo MP4.");
            }
            OptionalDouble duracao = Mp4Duracao.segundos(arquivo);
            if (duracao.isEmpty()) {
                throw new ValidacaoAnuncioException(
                        "Não foi possível ler a duração do vídeo. Envie um MP4 válido.");
            }
            if (duracao.getAsDouble() > duracaoMaximaSegundos) {
                throw new ValidacaoAnuncioException("O vídeo deve ter no máximo "
                        + (duracaoMaximaSegundos / 60) + " minutos.");
            }
            return new Inspecao("video/mp4", "mp4", null, null, (int) Math.ceil(duracao.getAsDouble()));
        } catch (IOException e) {
            throw new ValidacaoAnuncioException("Não foi possível ler o vídeo enviado.");
        }
    }

    /** Gera miniatura JPEG de ate 480 px de largura. Devolve false se nao conseguir decodificar. */
    public boolean gerarMiniatura(Path imagem, Path destino) {
        try {
            BufferedImage origem = ImageIO.read(imagem.toFile());
            if (origem == null) {
                return false;
            }
            int largura = Math.min(LARGURA_MINIATURA, origem.getWidth());
            int altura = Math.max(1, Math.round((float) origem.getHeight() * largura / origem.getWidth()));
            BufferedImage saida = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = saida.createGraphics();
            try {
                g.setColor(Color.WHITE); // PNG com transparencia vira fundo branco no JPEG
                g.fillRect(0, 0, largura, altura);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(origem.getScaledInstance(largura, altura, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
            } finally {
                g.dispose();
            }
            return ImageIO.write(saida, "jpg", destino.toFile());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String normalizar(String mime) {
        if (mime == null) {
            return "";
        }
        String m = mime.toLowerCase().trim();
        int ponto = m.indexOf(';');
        return ponto >= 0 ? m.substring(0, ponto).trim() : m;
    }

    private static String detectarImagem(Path arquivo) throws IOException {
        byte[] cab = new byte[8];
        try (InputStream in = Files.newInputStream(arquivo)) {
            if (in.readNBytes(cab, 0, 8) < 8) {
                return null;
            }
        }
        if ((cab[0] & 0xFF) == 0xFF && (cab[1] & 0xFF) == 0xD8 && (cab[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((cab[0] & 0xFF) == 0x89 && cab[1] == 'P' && cab[2] == 'N' && cab[3] == 'G'
                && cab[4] == 0x0D && cab[5] == 0x0A && cab[6] == 0x1A && cab[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    private static boolean pareceMp4(Path arquivo) throws IOException {
        byte[] cab = new byte[8];
        try (InputStream in = Files.newInputStream(arquivo)) {
            if (in.readNBytes(cab, 0, 8) < 8) {
                return false;
            }
        }
        // ISO-BMFF: os bytes 4..7 da primeira caixa sao o tipo ("ftyp").
        return cab[4] == 'f' && cab[5] == 't' && cab[6] == 'y' && cab[7] == 'p';
    }

    /** Le largura e altura pelo cabecalho, sem decodificar a imagem inteira. */
    private static int[] dimensoes(Path arquivo) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(arquivo.toFile())) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new ValidacaoAnuncioException("Não foi possível ler a imagem enviada.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(in);
                return new int[]{leitor.getWidth(0), leitor.getHeight(0)};
            } finally {
                leitor.dispose();
            }
        }
    }
}
