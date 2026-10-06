package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Teto de pixels configuravel: imagem-bomba e recusada antes de ser decodificada por inteiro. */
class MidiaPixelsTest {

    @TempDir
    Path pasta;

    private Path png(int largura, int altura) throws IOException {
        Path arq = pasta.resolve("img-" + largura + "x" + altura + ".png");
        ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), "png", arq.toFile());
        return arq;
    }

    @Test
    @DisplayName("CT1052 - Acima do teto de pixels a imagem e recusada com mensagem clara; abaixo, passa")
    void tetoDePixels() throws IOException {
        MidiaProperties props = new MidiaProperties(pasta.toString(), 10, 100);
        MidiaProcessador limitado = new MidiaProcessador(props, 1_000_000L);

        ValidacaoAnuncioException e = assertThrows(ValidacaoAnuncioException.class,
                () -> limitado.inspecionarImagem(png(1500, 1000), "image/png", false)); // 1,5 MP
        assertTrue(e.getMessage().contains("alta demais"), e.getMessage());

        assertEquals(1000, limitado.inspecionarImagem(png(1000, 999), "image/png", false).largura());
    }

    @Test
    @DisplayName("CT1053 - O teto padrao e de 30 megapixels (cabe em instancia de 512 MB)")
    void padrao() {
        assertEquals(30_000_000L, MidiaProcessador.PIXELS_PADRAO);
    }
}
