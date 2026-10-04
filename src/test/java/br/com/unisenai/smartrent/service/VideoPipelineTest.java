package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.MidiaProperties;
import br.com.unisenai.smartrent.config.VideoProperties;
import br.com.unisenai.smartrent.dto.ImovelResponse;
import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusVideo;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pipeline de video de ponta a ponta com banco (H2) e disco temporario: envio em
 * partes retomavel, validacao no servidor, processamento assincrono idempotente com
 * retry, estados, bloqueios de publicacao e preservacao no fluxo de edicao.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:videos;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({VideoUploadService.class, VideoWorker.class, MidiaService.class, MidiaProcessador.class, ImovelAcesso.class,
        MidiaStorageDisco.class, VideoPipelineTest.Config.class})
class VideoPipelineTest {

    /** Processamento basico com falha controlavel (para testar retry e FALHA). */
    static class ProcessamentoControlavel extends VideoProcessingBasico {
        int tentativas;
        boolean falhar;

        @Override
        public Resultado processar(Path original, Path pastaSaida) throws IOException {
            tentativas++;
            if (falhar) {
                throw new IOException("FFmpeg falhou (código 1): Invalid data found when processing input");
            }
            return super.processar(original, pastaSaida);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0));
        }

        @Bean
        MidiaProperties midiaProps() throws IOException {
            return new MidiaProperties(Files.createTempDirectory("smartrent-videos-teste").toString(), 10, 100);
        }

        @Bean
        VideoProperties videoProps() {
            return new VideoProperties("", "", 90, 1, 2160, 1, true, 600, 2, 24); // 1 MB, 2 tentativas
        }

        @Bean
        ProcessamentoControlavel processamento() {
            return new ProcessamentoControlavel();
        }
    }

    @Autowired private TestEntityManager em;
    @Autowired private RelogioFalso relogio;
    @Autowired private VideoUploadService uploads;
    @Autowired private VideoWorker worker;
    @Autowired private MidiaService midiaService;
    @Autowired private MidiaStorage storage;
    @Autowired private ProcessamentoControlavel processamento;
    @Autowired private ImovelMidiaRepository midiaRepository;

    private Usuario gestor;
    private Usuario outro;
    private Imovel imovel;

    @BeforeEach
    void preparar() {
        processamento.falhar = false;
        processamento.tentativas = 0;
        gestor = usuario("Gestor Um");
        outro = usuario("Gestor Dois");
        imovel = novoImovel(gestor, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        em.flush();
    }

    private Usuario usuario(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(PapelUsuario.ANFITRIAO);
        u.setAtivo(true);
        return em.persist(u);
    }

    private Imovel novoImovel(Usuario dono, StatusAnuncio status) {
        Imovel i = new Imovel();
        i.setUsuario(dono);
        i.setTitulo("Apto");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(4);
        i.setNumeroQuartos(1);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setStatus(status);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        return em.persist(i);
    }

    /** Envia um MP4 de {@code segundos} segundos inteiro, em partes de {@code tamanhoParte} bytes. */
    private MidiaResponse enviar(Imovel alvo, int segundos, int tamanhoParte) {
        byte[] conteudo = Mp4Falso.comDuracao(segundos, 2000);
        MidiaResponse m = uploads.iniciar(gestor, alvo.getId(), conteudo.length, "video/mp4");
        for (int off = 0; off < conteudo.length; off += tamanhoParte) {
            int fim = Math.min(conteudo.length, off + tamanhoParte);
            uploads.enviarParte(gestor, alvo.getId(), m.id(), off, new ByteArrayInputStream(conteudo, off, fim - off));
        }
        return uploads.concluir(gestor, alvo.getId(), m.id());
    }

    private ImovelMidia recarregar(Long id) {
        em.flush();
        em.clear();
        return midiaRepository.findById(id).orElseThrow();
    }

    // ------------------------------------------------------------- limite 1:30

    @Test
    @DisplayName("CT600 - Video de exatamente 90 s e aceito; vai para PROCESSANDO")
    void aceita90Segundos() {
        MidiaResponse m = enviar(imovel, 90, 700);
        assertEquals(StatusVideo.PROCESSANDO, m.statusProcessamento());
        assertEquals(90, m.duracaoSegundos());
    }

    @Test
    @DisplayName("CT601 - Video de 91 s e rejeitado NO SERVIDOR com 'tem 1:31; o maximo e 1:30'; nada fica gravado")
    void rejeita91Segundos() {
        byte[] conteudo = Mp4Falso.comDuracao(91, 2000);
        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), conteudo.length, "video/mp4");
        uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(conteudo));

        var erro = assertThrows(ValidacaoAnuncioException.class, () -> uploads.concluir(gestor, imovel.getId(), m.id()));

        assertEquals("O vídeo tem 1:31; o máximo permitido é 1:30.", erro.getMessage());
        em.flush();
        em.clear();
        assertTrue(midiaRepository.findById(m.id()).isEmpty(), "a midia invalida e apagada");
        assertFalse(Files.exists(storage.caminhoLocal("videos/" + chaveDe(m) + "/original.mp4")));
    }

    private String chaveDe(MidiaResponse m) {
        return m.url().substring(m.url().lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("CT602 - O terceiro video e rejeitado; videos nao entram nas 14 imagens")
    void limiteDeDoisVideos() {
        enviar(imovel, 30, 5000);
        enviar(imovel, 30, 5000);
        var erro = assertThrows(ValidacaoAnuncioException.class,
                () -> uploads.iniciar(gestor, imovel.getId(), 1000, "video/mp4"));
        assertTrue(erro.getMessage().contains("Limite de 2 vídeos"));

        // 14 imagens ja no anuncio nao impedem os videos (cota separada)
        Imovel outroImovel = novoImovel(gestor, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        for (int i = 0; i < 14; i++) {
            ImovelMidia img = new ImovelMidia();
            img.setImovel(outroImovel);
            img.setChave(java.util.UUID.randomUUID().toString());
            img.setTipo(TipoMidia.FOTO);
            img.setEstado(EstadoMidia.ATIVA);
            img.setArquivo("x" + i + ".jpg");
            img.setMime("image/jpeg");
            img.setTamanhoBytes(10);
            img.setOrdem(i);
            em.persist(img);
        }
        em.flush();
        assertDoesNotThrow(() -> uploads.iniciar(gestor, outroImovel.getId(), 1000, "video/mp4"));
    }

    @Test
    @DisplayName("CT603 - Arquivo com extensao e MIME de video mas conteudo invalido e rejeitado (tipo real pelo conteudo)")
    void conteudoInvalido() {
        byte[] lixo = "isto nao e um video, mas tem mais de doze bytes".getBytes(StandardCharsets.UTF_8);
        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), lixo.length, "video/mp4");
        uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(lixo));

        var erro = assertThrows(ValidacaoAnuncioException.class, () -> uploads.concluir(gestor, imovel.getId(), m.id()));
        assertTrue(erro.getMessage().contains("não é um vídeo"), erro.getMessage());
        em.flush();
        em.clear();
        assertTrue(midiaRepository.findById(m.id()).isEmpty());
    }

    @Test
    @DisplayName("CT604 - Arquivo acima do tamanho maximo e rejeitado ao iniciar; parte que passa do tamanho informado tambem")
    void tamanhoMaximo() {
        var erro = assertThrows(ValidacaoAnuncioException.class,
                () -> uploads.iniciar(gestor, imovel.getId(), 2L * 1024 * 1024, "video/mp4")); // limite do teste: 1 MB
        assertTrue(erro.getMessage().contains("excede o tamanho máximo de 1 MB"));

        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), 10, "video/mp4");
        assertThrows(ValidacaoAnuncioException.class,
                () -> uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(new byte[50])));
    }

    @Test
    @DisplayName("CT605 - Formatos fora de MP4/MOV (e WebM sem FFmpeg) sao rejeitados ao iniciar")
    void formatos() {
        assertThrows(ValidacaoAnuncioException.class, () -> uploads.iniciar(gestor, imovel.getId(), 100, "video/x-msvideo"));
        var webm = assertThrows(ValidacaoAnuncioException.class, () -> uploads.iniciar(gestor, imovel.getId(), 100, "video/webm"));
        assertTrue(webm.getMessage().contains("FFmpeg"));
        assertDoesNotThrow(() -> uploads.iniciar(gestor, imovel.getId(), 100, "video/quicktime"));
    }

    // ------------------------------------------------- envio em partes retomavel

    @Test
    @DisplayName("CT610 - Envio retomavel: parte fora de ordem recebe 409 com os bytes ja gravados; reenviar parte repetida nao muda nada")
    void envioRetomavel() {
        byte[] conteudo = Mp4Falso.comDuracao(30, 2000);
        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), conteudo.length, "video/mp4");
        assertEquals(StatusVideo.ENVIANDO, m.statusProcessamento());
        assertEquals(0L, m.bytesRecebidos());

        MidiaResponse p1 = uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(conteudo, 0, 500));
        assertEquals(500L, p1.bytesRecebidos());

        // a conexao caiu e o cliente tentou pular: o servidor diz onde continuar
        var pulo = assertThrows(ConflitoComDetalhesException.class,
                () -> uploads.enviarParte(gestor, imovel.getId(), m.id(), 900, new ByteArrayInputStream(conteudo, 900, 100)));
        assertEquals("OFFSET_INESPERADO", pulo.codigo());
        assertEquals(500L, uploads.estado(gestor, imovel.getId(), m.id()).bytesRecebidos(), "estado de retomada");

        // reenvio da mesma parte (resposta perdida): idempotente
        assertEquals(500L, uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(conteudo, 0, 500)).bytesRecebidos());

        // concluir antes da hora
        assertThrows(ValidacaoAnuncioException.class, () -> uploads.concluir(gestor, imovel.getId(), m.id()));

        // retoma do ponto certo e conclui
        uploads.enviarParte(gestor, imovel.getId(), m.id(), 500, new ByteArrayInputStream(conteudo, 500, conteudo.length - 500));
        assertEquals(StatusVideo.PROCESSANDO, uploads.concluir(gestor, imovel.getId(), m.id()).statusProcessamento());
    }

    @Test
    @DisplayName("CT611 - Envios e consultas so do dono do imovel")
    void soODono() {
        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), 100, "video/mp4");
        assertThrows(AcessoNegadoException.class, () -> uploads.iniciar(outro, imovel.getId(), 100, "video/mp4"));
        assertThrows(AcessoNegadoException.class, () -> uploads.enviarParte(outro, imovel.getId(), m.id(), 0, new ByteArrayInputStream(new byte[10])));
        assertThrows(AcessoNegadoException.class, () -> uploads.estado(outro, imovel.getId(), m.id()));
        assertThrows(AcessoNegadoException.class, () -> uploads.cancelar(outro, imovel.getId(), m.id()));
    }

    @Test
    @DisplayName("CT612 - Cancelar o envio apaga a parte gravada e a midia")
    void cancelarEnvio() {
        MidiaResponse m = uploads.iniciar(gestor, imovel.getId(), 100, "video/mp4");
        uploads.enviarParte(gestor, imovel.getId(), m.id(), 0, new ByteArrayInputStream(new byte[40]));
        Path parcial = storage.caminhoLocal("videos/" + chaveDe(m) + "/parcial.part");
        assertTrue(Files.exists(parcial));

        uploads.cancelar(gestor, imovel.getId(), m.id());

        em.flush();
        em.clear();
        assertTrue(midiaRepository.findById(m.id()).isEmpty());
        assertFalse(Files.exists(parcial));
    }

    // ----------------------------------------------------------- processamento

    @Test
    @DisplayName("CT620 - Worker processa de forma assincrona: PROCESSANDO -> PRONTO, publica o MP4 e apaga o original")
    void workerProcessa() {
        MidiaResponse m = enviar(imovel, 45, 5000);
        String chave = chaveDe(m);
        assertTrue(Files.exists(storage.caminhoLocal("videos/" + chave + "/original.mp4")));

        assertEquals(1, worker.processarPendentes());

        ImovelMidia salvo = recarregar(m.id());
        assertEquals(StatusVideo.PRONTO, salvo.getStatusProcessamento());
        assertEquals("videos/" + chave + "/video.mp4", salvo.getArquivo());
        assertEquals("video/mp4", salvo.getMime());
        assertTrue(Files.exists(storage.caminhoLocal(salvo.getArquivo())));
        assertFalse(Files.exists(storage.caminhoLocal("videos/" + chave + "/original.mp4")), "original apagado apos o sucesso");
        assertEquals(0, worker.processarPendentes(), "nada mais na fila");
    }

    @Test
    @DisplayName("CT621 - Job idempotente: processar um video ja PRONTO nao reprocessa nem duplica arquivos")
    void workerIdempotente() {
        MidiaResponse m = enviar(imovel, 45, 5000);
        worker.processar(m.id());
        int chamadas = processamento.tentativas;
        long arquivos = contarArquivos(chaveDe(m));

        worker.processar(m.id());
        worker.processar(m.id());

        assertEquals(chamadas, processamento.tentativas, "nao reprocessa");
        assertEquals(arquivos, contarArquivos(chaveDe(m)));
        assertEquals(StatusVideo.PRONTO, recarregar(m.id()).getStatusProcessamento());
    }

    private long contarArquivos(String chave) {
        try (var s = Files.walk(storage.caminhoLocal("videos/" + chave))) {
            return s.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("CT622 - Falha gera retry com espera crescente e, esgotadas as tentativas, estado FALHA com mensagem para o gestor")
    void retryEFalha() {
        MidiaResponse m = enviar(imovel, 45, 5000);
        processamento.falhar = true;

        worker.processar(m.id());
        ImovelMidia t1 = recarregar(m.id());
        assertEquals(StatusVideo.PROCESSANDO, t1.getStatusProcessamento());
        assertEquals(1, t1.getTentativas());
        assertNotNull(t1.getProximaTentativaEm());
        assertTrue(t1.getMotivoFalha().contains("nova tentativa automática"));
        assertEquals(0, worker.processarPendentes(), "ainda dentro da espera do backoff");

        relogio.avancar(Duration.ofMinutes(5));
        assertEquals(1, worker.processarPendentes());
        ImovelMidia t2 = recarregar(m.id());
        assertEquals(StatusVideo.FALHA, t2.getStatusProcessamento(), "limite de 2 tentativas do teste");
        assertTrue(t2.getMotivoFalha().contains("Remova-o e envie novamente"));
        assertNull(t2.getProximaTentativaEm());
        assertEquals(2, processamento.tentativas);
    }

    @Test
    @DisplayName("CT623 - Falha transitoria seguida de sucesso termina PRONTO e limpa o motivo")
    void falhaTransitoria() {
        MidiaResponse m = enviar(imovel, 45, 5000);
        processamento.falhar = true;
        worker.processar(m.id());
        processamento.falhar = false;
        relogio.avancar(Duration.ofMinutes(5));
        worker.processarPendentes();

        ImovelMidia salvo = recarregar(m.id());
        assertEquals(StatusVideo.PRONTO, salvo.getStatusProcessamento());
        assertNull(salvo.getMotivoFalha());
    }

    // -------------------------------------------------------- bloqueios e exibicao

    @Test
    @DisplayName("CT630 - Publicar ou confirmar exige videos PRONTOS: ENVIANDO, PROCESSANDO e FALHA bloqueiam; remover o video libera")
    void bloqueiaSemVideoPronto() {
        assertDoesNotThrow(() -> midiaService.exigirVideosProntos(imovel.getId()), "sem videos nao ha o que esperar");

        byte[] conteudo = Mp4Falso.comDuracao(30, 100);
        MidiaResponse enviando = uploads.iniciar(gestor, imovel.getId(), conteudo.length, "video/mp4");
        var e1 = assertThrows(ValidacaoAnuncioException.class, () -> midiaService.exigirVideosProntos(imovel.getId()));
        assertTrue(e1.getMessage().contains("Aguarde o fim do envio e do processamento"));

        uploads.enviarParte(gestor, imovel.getId(), enviando.id(), 0, new ByteArrayInputStream(conteudo));
        uploads.concluir(gestor, imovel.getId(), enviando.id());
        assertThrows(ValidacaoAnuncioException.class, () -> midiaService.exigirVideosProntos(imovel.getId()), "PROCESSANDO");

        processamento.falhar = true;
        worker.processar(enviando.id());
        relogio.avancar(Duration.ofMinutes(5));
        worker.processarPendentes();
        var e2 = assertThrows(ValidacaoAnuncioException.class, () -> midiaService.exigirVideosProntos(imovel.getId()));
        assertTrue(e2.getMessage().contains("falha"), "FALHA");

        midiaService.remover(gestor, imovel.getId(), enviando.id()); // remover para seguir
        assertDoesNotThrow(() -> midiaService.exigirVideosProntos(imovel.getId()));

        processamento.falhar = false;
        MidiaResponse ok = enviar(imovel, 30, 5000);
        worker.processar(ok.id());
        assertDoesNotThrow(() -> midiaService.exigirVideosProntos(imovel.getId()), "PRONTO");
    }

    @Test
    @DisplayName("CT631 - No anuncio publico so aparecem videos PRONTO; ENVIANDO, PROCESSANDO e FALHA ficam de fora")
    void soProntoAparece() {
        MidiaResponse pronto = enviar(imovel, 30, 5000);
        worker.processar(pronto.id());
        MidiaResponse processando = enviar(imovel, 30, 5000);
        em.flush();
        em.clear();

        List<ImovelMidia> todas = midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(imovel.getId());
        ImovelResponse r = ImovelResponse.de(em.find(Imovel.class, imovel.getId()), todas, true);
        assertEquals(1, r.midias().size());
        assertEquals(pronto.id(), r.midias().get(0).id());
        assertFalse(todas.stream().filter(m -> m.getId().equals(processando.id())).findFirst().orElseThrow().visivelAoPublico());
    }

    // --------------------------------------------------------------- orfaos

    @Test
    @DisplayName("CT640 - Envios abandonados ha mais de 24 h sao limpos (arquivos e registro); os recentes ficam")
    void limpezaDeOrfaos() {
        MidiaResponse velho = uploads.iniciar(gestor, imovel.getId(), 1000, "video/mp4");
        uploads.enviarParte(gestor, imovel.getId(), velho.id(), 0, new ByteArrayInputStream(new byte[100]));
        Path parcialVelho = storage.caminhoLocal("videos/" + chaveDe(velho) + "/parcial.part");

        relogio.avancar(Duration.ofHours(25));
        MidiaResponse novo = uploads.iniciar(gestor, imovel.getId(), 1000, "video/mp4");

        assertEquals(1, uploads.limparOrfaos());
        em.flush();
        em.clear();
        assertTrue(midiaRepository.findById(velho.id()).isEmpty());
        assertFalse(Files.exists(parcialVelho));
        assertTrue(midiaRepository.findById(novo.id()).isPresent());
        assertEquals(0, uploads.limparOrfaos());
    }

    // ------------------------------------------------------------------ edicao

    @Test
    @DisplayName("CT650 - Na edicao o video antigo permanece ate confirmar (arquivos intactos); confirmar apaga o substituido; descartar preserva o antigo")
    void edicaoPreservaVideoAntigo() {
        MidiaResponse antigo = enviar(imovel, 30, 5000);
        worker.processar(antigo.id());
        String chaveAntigo = chaveDe(antigo);
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        em.persist(imovel);
        em.flush();

        // marca o antigo como removido e envia um novo (NOVA): o antigo continua no anuncio vivo
        midiaService.remover(gestor, imovel.getId(), antigo.id());
        MidiaResponse novo = enviar(imovel, 40, 5000);
        worker.processar(novo.id());
        assertEquals(EstadoMidia.REMOVIDA, recarregar(antigo.id()).getEstado());
        assertTrue(Files.exists(storage.caminhoLocal("videos/" + chaveAntigo + "/video.mp4")), "antigo ainda no armazenamento");

        // descartar: volta o antigo, some o novo
        midiaService.desfazerRascunho(imovel);
        em.flush();
        em.clear();
        assertEquals(EstadoMidia.ATIVA, midiaRepository.findById(antigo.id()).orElseThrow().getEstado());
        assertTrue(midiaRepository.findById(novo.id()).isEmpty());
        assertTrue(Files.exists(storage.caminhoLocal("videos/" + chaveAntigo + "/video.mp4")), "antigo preservado no descarte");

        // de novo: substituir e confirmar apaga o antigo do armazenamento
        imovel = em.find(Imovel.class, imovel.getId());
        midiaService.remover(gestor, imovel.getId(), antigo.id());
        MidiaResponse novo2 = enviar(imovel, 40, 5000);
        worker.processar(novo2.id());
        midiaService.aplicarRascunho(imovel, new AnuncioRascunho());
        em.flush();
        em.clear();
        assertTrue(midiaRepository.findById(antigo.id()).isEmpty());
        assertFalse(Files.exists(storage.caminhoLocal("videos/" + chaveAntigo + "/video.mp4")), "antigo apagado so apos confirmar");
        assertEquals(EstadoMidia.ATIVA, midiaRepository.findById(novo2.id()).orElseThrow().getEstado());
    }
}
