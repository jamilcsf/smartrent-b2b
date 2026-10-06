package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** Saneamento das fotos de anuncio: sem EXIF/GPS/XMP, com orientacao aplicada, e job idempotente (H2 + disco temporario). */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:saneamento;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({MidiaProcessador.class, MidiaStorageDisco.class, SaneamentoFotosAnuncio.class, SaneamentoImagensAnuncioTest.Config.class})
class SaneamentoImagensAnuncioTest {

    private static final String MARCA_GPS = "GPS-LAT-27.5954S-LON-48.5480W";

    @TestConfiguration
    static class Config {
        @Bean
        MidiaProperties midiaProps() throws IOException {
            return new MidiaProperties(Files.createTempDirectory("smartrent-saneamento").toString(), 10, 100);
        }
    }

    @Autowired private MidiaProcessador processador;
    @Autowired private MidiaStorage storage;
    @Autowired private SaneamentoFotosAnuncio job;
    @Autowired private ImovelMidiaRepository midias;
    @Autowired private TestEntityManager em;

    // ------------------------------------------------------------ fabricas

    /** Esquerda vermelha, direita azul. */
    private static BufferedImage imagem(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void u16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    private static void u32(ByteArrayOutputStream o, int v) {
        u16(o, v & 0xFFFF);
        u16(o, (v >>> 16) & 0xFFFF);
    }

    /** JPEG com APP1/EXIF (orientacao + ponteiro de GPS + texto de GPS), APP1/XMP e comentario com coordenadas. */
    private static byte[] jpegComMetadados(BufferedImage img, int orientacao) throws IOException {
        byte[] base = jpeg(img);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write("II".getBytes(StandardCharsets.US_ASCII));
        u16(tiff, 42);
        u32(tiff, 8);                       // IFD0 logo apos o cabecalho
        u16(tiff, 2);                       // duas entradas
        u16(tiff, 0x0112); u16(tiff, 3); u32(tiff, 1); u16(tiff, orientacao); u16(tiff, 0);
        u16(tiff, 0x8825); u16(tiff, 4); u32(tiff, 1); u32(tiff, 38);   // ponteiro do GPS IFD
        u32(tiff, 0);                       // fim do IFD0
        tiff.write(MARCA_GPS.getBytes(StandardCharsets.US_ASCII));      // "dados" de GPS

        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        exif.write(tiff.toByteArray());
        ByteArrayOutputStream xmp = new ByteArrayOutputStream();
        xmp.write("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta GPSLatitude=\"27,35S\"/>".getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(base, 0, 2); // SOI
        segmento(saida, 0xE1, exif.toByteArray());
        segmento(saida, 0xE1, xmp.toByteArray());
        segmento(saida, 0xFE, MARCA_GPS.getBytes(StandardCharsets.US_ASCII));
        saida.write(base, 2, base.length - 2);
        return saida.toByteArray();
    }

    private static void segmento(ByteArrayOutputStream o, int marcador, byte[] corpo) {
        o.write(0xFF);
        o.write(marcador);
        int tamanho = corpo.length + 2;
        o.write(tamanho >> 8);
        o.write(tamanho & 0xFF);
        o.write(corpo, 0, corpo.length);
    }

    private static byte[] pngComTexto(BufferedImage img) throws IOException {
        byte[] base = png(img);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, 33); // assinatura + IHDR
        byte[] corpo = ("Comment\0" + MARCA_GPS).getBytes(StandardCharsets.ISO_8859_1);
        byte[] tipo = "tEXt".getBytes(StandardCharsets.US_ASCII);
        out.write(new byte[]{0, 0, 0, (byte) corpo.length});
        out.write(tipo);
        out.write(corpo);
        CRC32 crc = new CRC32();
        crc.update(tipo);
        crc.update(corpo);
        long c = crc.getValue();
        out.write(new byte[]{(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
        out.write(base, 33, base.length - 33);
        return out.toByteArray();
    }

    private static boolean contem(byte[] dados, String texto) {
        return new String(dados, StandardCharsets.ISO_8859_1).contains(texto);
    }

    private Path arquivo(byte[] bytes, String sufixo) throws IOException {
        Path p = Files.createTempFile("smartrent-teste-", sufixo);
        Files.write(p, bytes);
        p.toFile().deleteOnExit();
        return p;
    }

    // --------------------------------------------------------------- testes

    @Test
    @DisplayName("JPEG com EXIF/GPS, XMP e comentario sai sem nenhum metadado e com a orientacao aplicada")
    void jpegSaiLimpoEOrientado() throws IOException {
        byte[] original = jpegComMetadados(imagem(40, 20), 6); // 6 = girar 90 graus horario
        assertTrue(contem(original, "Exif") && contem(original, MARCA_GPS) && contem(original, "xmpmeta"));
        Path arq = arquivo(original, ".jpg");

        MidiaProcessador.Inspecao insp = processador.inspecionarImagem(arq, "image/jpeg", false);
        assertEquals(20, insp.largura(), "dimensao ja considera a orientacao");
        MidiaProcessador.Inspecao nova = processador.sanearImagem(arq, insp);

        byte[] limpo = Files.readAllBytes(arq);
        assertFalse(contem(limpo, "Exif"));
        assertFalse(contem(limpo, "xmpmeta"));
        assertFalse(contem(limpo, "ns.adobe.com"));
        assertFalse(contem(limpo, MARCA_GPS));
        assertFalse(contem(limpo, "GPS"));
        assertEquals(1, ImagemSegura.orientacaoExif(limpo), "sem tag de orientacao");
        // nenhum segmento APP1..APP15 nem COM antes dos dados da imagem
        for (int i = 2; i + 3 < limpo.length && (limpo[i] & 0xFF) == 0xFF; ) {
            int marcador = limpo[i + 1] & 0xFF;
            if (marcador == 0xDA) {
                break;
            }
            assertFalse(marcador >= 0xE1 && marcador <= 0xEF || marcador == 0xFE, "marcador inesperado " + Integer.toHexString(marcador));
            i += 2 + (((limpo[i + 2] & 0xFF) << 8) | (limpo[i + 3] & 0xFF));
        }

        BufferedImage saida = ImageIO.read(new ByteArrayInputStream(limpo));
        assertEquals(20, saida.getWidth());
        assertEquals(40, saida.getHeight());
        assertEquals(20, nova.largura());
        assertEquals(40, nova.altura());
        // vermelho a esquerda vira vermelho no topo; azul a direita vira azul embaixo
        assertTrue(new Color(saida.getRGB(10, 5)).getRed() > 150, "topo vermelho");
        assertTrue(new Color(saida.getRGB(10, 35)).getBlue() > 150, "base azul");
    }

    @Test
    @DisplayName("JPEG sem rotacao tambem perde o EXIF e mantem as dimensoes")
    void jpegSemRotacao() throws IOException {
        Path arq = arquivo(jpegComMetadados(imagem(40, 20), 1), ".jpg");
        MidiaProcessador.Inspecao insp = processador.inspecionarImagem(arq, "image/jpeg", false);
        MidiaProcessador.Inspecao nova = processador.sanearImagem(arq, insp);
        assertEquals(40, nova.largura());
        assertEquals(20, nova.altura());
        assertFalse(contem(Files.readAllBytes(arq), "Exif"));
    }

    @Test
    @DisplayName("PNG continua aceito, continua PNG e perde os blocos de texto")
    void pngContinuaAceito() throws IOException {
        byte[] original = pngComTexto(imagem(30, 30));
        assertTrue(contem(original, MARCA_GPS));
        Path arq = arquivo(original, ".png");
        MidiaProcessador.Inspecao insp = processador.inspecionarImagem(arq, "image/png", false);
        assertEquals("image/png", insp.mime());
        processador.sanearImagem(arq, insp);
        byte[] limpo = Files.readAllBytes(arq);
        assertEquals("png", ImagemSegura.formatoPelosBytes(limpo));
        assertFalse(contem(limpo, MARCA_GPS));
        assertFalse(contem(limpo, "tEXt"));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(limpo)));
    }

    @Test
    @DisplayName("Arquivo .jpg cujo conteudo nao e imagem e rejeitado")
    void extensaoMentirosa() throws IOException {
        Path arq = arquivo("<?php echo 1; ?> isto nao e uma imagem de verdade".getBytes(StandardCharsets.UTF_8), ".jpg");
        assertThrows(ValidacaoAnuncioException.class, () -> processador.inspecionarImagem(arq, "image/jpeg", false));
    }

    @Test
    @DisplayName("Foto 360 continua exigindo 2:1; a regra vale para a imagem ja orientada")
    void regra360PermaneceEPrecisaDaOrientacao() throws IOException {
        Path ok = arquivo(jpegComMetadados(imagem(80, 40), 1), ".jpg");
        assertEquals(80, processador.inspecionarImagem(ok, "image/jpeg", true).largura());
        Path torta = arquivo(jpegComMetadados(imagem(60, 40), 1), ".jpg");
        assertThrows(ValidacaoAnuncioException.class, () -> processador.inspecionarImagem(torta, "image/jpeg", true));
        // 40x80 com orientacao 6 e exibida como 80x40: vale como 360
        Path girada = arquivo(jpegComMetadados(imagem(40, 80), 6), ".jpg");
        assertDoesNotThrow(() -> processador.inspecionarImagem(girada, "image/jpeg", true));
    }

    // ---------------------------------------------------------------- job

    private ImovelMidia fotoAntiga(Imovel imovel, String chave, byte[] bytes) throws IOException {
        Path tmp = arquivo(bytes, ".jpg");
        String nome = chave + ".jpg";
        storage.salvar(nome, tmp);
        ImovelMidia m = new ImovelMidia();
        m.setImovel(imovel);
        m.setChave(chave);
        m.setTipo(TipoMidia.FOTO);
        m.setEstado(EstadoMidia.ATIVA);
        m.setArquivo(nome);
        m.setMime("image/jpeg");
        m.setTamanhoBytes(bytes.length);
        m.setLargura(40);
        m.setAltura(20);
        m.setOrdem(0);
        m.setDataEnvio(LocalDateTime.of(2026, 10, 1, 12, 0));
        return em.persist(m);
    }

    private Imovel imovel() {
        Usuario u = new Usuario();
        u.setNome("Gestor");
        u.setEmail("gestor" + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(PapelUsuario.ANFITRIAO);
        u.setAtivo(true);
        em.persist(u);
        Imovel i = new Imovel();
        i.setUsuario(u);
        i.setTitulo("Apto");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(2);
        i.setNumeroQuartos(1);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("200.00"));
        i.setStatus(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        return em.persist(i);
    }

    @Test
    @DisplayName("Job de saneamento reprocessa as fotos antigas uma vez e e idempotente")
    void jobIdempotente() throws IOException {
        Imovel imovel = imovel();
        ImovelMidia antiga = fotoAntiga(imovel, "antiga-1", jpegComMetadados(imagem(40, 20), 6));
        ImovelMidia jaLimpa = fotoAntiga(imovel, "limpa-1", jpeg(imagem(40, 20)));
        jaLimpa.setMetadadosRemovidos(true);
        byte[] bytesJaLimpa = Files.readAllBytes(storage.caminhoLocal("limpa-1.jpg"));
        ImovelMidia quebrada = fotoAntiga(imovel, "quebrada-1", "isto nao e imagem".getBytes(StandardCharsets.UTF_8));
        em.flush();
        em.clear();

        SaneamentoFotosAnuncio.Resultado primeira = job.executar();
        assertEquals(1, primeira.saneadas());
        assertEquals(1, primeira.falhas());

        byte[] depois = Files.readAllBytes(storage.caminhoLocal("antiga-1.jpg"));
        assertFalse(contem(depois, "Exif"));
        assertFalse(contem(depois, MARCA_GPS));
        ImovelMidia relida = midias.findById(antiga.getId()).orElseThrow();
        assertTrue(relida.isMetadadosRemovidos());
        assertEquals(20, relida.getLargura());
        assertEquals(40, relida.getAltura());
        assertEquals(depois.length, relida.getTamanhoBytes());
        assertArrayEquals(bytesJaLimpa, Files.readAllBytes(storage.caminhoLocal("limpa-1.jpg")), "foto ja limpa nao e tocada");
        assertFalse(midias.findById(quebrada.getId()).orElseThrow().isMetadadosRemovidos(), "falha fica para a proxima");

        SaneamentoFotosAnuncio.Resultado segunda = job.executar();
        assertEquals(0, segunda.saneadas(), "nada a reprocessar");
        assertEquals(1, segunda.falhas(), "so a que nunca decodifica");
        assertArrayEquals(depois, Files.readAllBytes(storage.caminhoLocal("antiga-1.jpg")), "segunda execucao nao recodifica");
    }
}
