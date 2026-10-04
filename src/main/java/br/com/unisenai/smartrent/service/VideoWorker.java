package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.VideoProperties;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.enums.StatusVideo;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Worker do processamento de video: fora da requisicao HTTP (a fila e a propria
 * tabela de midias, em PROCESSANDO). O trabalho e idempotente (repetir sobrescreve
 * os mesmos arquivos e nao duplica nada), com retry e espera crescente; ao esgotar
 * as tentativas o video vira FALHA com uma mensagem para o gestor.
 */
@Component
public class VideoWorker {

    private static final Logger log = LoggerFactory.getLogger(VideoWorker.class);

    private final ImovelMidiaRepository repo;
    private final VideoProcessingService processamento;
    private final MidiaStorage storage;
    private final VideoProperties props;
    private final VideoUploadService uploads;
    private final Clock clock;
    private final TransactionTemplate tx;

    public VideoWorker(ImovelMidiaRepository repo, VideoProcessingService processamento, MidiaStorage storage,
                       VideoProperties props, VideoUploadService uploads, Clock clock, PlatformTransactionManager tm) {
        this.repo = repo;
        this.processamento = processamento;
        this.storage = storage;
        this.props = props;
        this.uploads = uploads;
        this.clock = clock;
        this.tx = new TransactionTemplate(tm);
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.video-intervalo-ms:5000}", initialDelay = 20_000)
    public void agendado() {
        try {
            processarPendentes();
        } catch (RuntimeException e) {
            log.error("Ciclo do worker de video falhou; tentara de novo.", e);
        }
    }

    /** Limpeza periodica dos envios abandonados. */
    @Scheduled(fixedDelayString = "${smartrent.jobs.video-orfaos-intervalo-ms:3600000}", initialDelay = 120_000)
    public void limparOrfaos() {
        try {
            uploads.limparOrfaos();
        } catch (RuntimeException e) {
            log.error("Limpeza de envios de video abandonados falhou.", e);
        }
    }

    /** Processa os videos que ja podem ser tentados. Devolve quantos foram tentados. */
    public int processarPendentes() {
        List<Long> ids = tx.execute(s -> repo.findParaProcessar(clock.instant(), PageRequest.of(0, 3)).stream()
                .map(ImovelMidia::getId).toList());
        if (ids == null) {
            return 0;
        }
        ids.forEach(this::processar);
        return ids.size();
    }

    /** Uma tentativa. Idempotente: video ja PRONTO (ou fora da fila) e ignorado. */
    public void processar(Long midiaId) {
        ImovelMidia alvo = tx.execute(s -> {
            ImovelMidia m = repo.findById(midiaId).orElse(null);
            if (m == null || m.getStatusProcessamento() != StatusVideo.PROCESSANDO) {
                return null;
            }
            m.setTentativas(m.getTentativas() + 1);
            m.setAtualizadoEm(clock.instant());
            return repo.save(m);
        });
        if (alvo == null) {
            return;
        }
        String chave = alvo.getChave();
        String original = alvo.getOriginalArquivo();
        Path pasta = null;
        try {
            pasta = Files.createTempDirectory("smartrent-video-");
            VideoProcessingService.Resultado r = processamento.processar(storage.caminhoLocal(original), pasta);
            String prefixo = "videos/" + chave + "/";
            storage.removerPrefixo(prefixo + "hls"); // reprocessar nao deixa segmentos antigos para tras
            copiarSaida(pasta, prefixo);

            String mp4 = prefixo + pasta.relativize(r.mp4()).toString().replace('\\', '/');
            String poster = r.poster() == null ? null : prefixo + pasta.relativize(r.poster()).toString().replace('\\', '/');
            String hls = r.hlsMestre() == null ? null : prefixo + pasta.relativize(r.hlsMestre()).toString().replace('\\', '/');
            long tamanho = Files.size(r.mp4());
            VideoProcessingService.Metadados meta = r.metadados();

            tx.executeWithoutResult(s -> {
                ImovelMidia m = repo.findById(midiaId).orElseThrow();
                m.setArquivo(mp4);
                m.setMime("video/mp4");
                m.setTamanhoBytes(tamanho);
                m.setPoster(poster);
                m.setHlsMestre(hls);
                m.setDuracaoSegundos((int) Math.ceil(meta.duracaoSegundos()));
                if (meta.largura() > 0) {
                    m.setLargura(meta.largura());
                    m.setAltura(meta.altura());
                }
                m.setStatusProcessamento(StatusVideo.PRONTO);
                m.setMotivoFalha(null);
                m.setProximaTentativaEm(null);
                m.setAtualizadoEm(clock.instant());
                repo.save(m);
            });
            if (props.apagarOriginal() && !original.equals(mp4)) {
                storage.remover(original); // politica configuravel: o original nao e mais necessario
            }
            log.info("Video {} processado ({}).", chave, processamento.descricao());
        } catch (IOException | RuntimeException e) {
            registrarFalha(midiaId, e);
        } finally {
            apagarPasta(pasta);
        }
    }

    private void copiarSaida(Path pasta, String prefixo) throws IOException {
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            for (Path p : (Iterable<Path>) arquivos.filter(Files::isRegularFile)::iterator) {
                storage.salvar(prefixo + pasta.relativize(p).toString().replace('\\', '/'), p);
            }
        }
    }

    private void registrarFalha(Long midiaId, Exception e) {
        log.warn("Falha ao processar o video {}: {}", midiaId, e.getMessage());
        tx.executeWithoutResult(s -> {
            ImovelMidia m = repo.findById(midiaId).orElse(null);
            if (m == null) {
                return;
            }
            Instant agora = clock.instant();
            m.setAtualizadoEm(agora);
            if (m.getTentativas() < props.maxTentativas()) {
                long espera = 30L * (1L << Math.min(m.getTentativas() - 1, 6)); // 30 s, 60 s, 120 s...
                m.setProximaTentativaEm(agora.plusSeconds(espera));
                m.setMotivoFalha("Tentativa " + m.getTentativas() + " de " + props.maxTentativas() + " falhou; nova tentativa automática.");
            } else {
                m.setStatusProcessamento(StatusVideo.FALHA);
                m.setProximaTentativaEm(null);
                m.setMotivoFalha("Não foi possível processar este vídeo. Remova-o e envie novamente (MP4 ou MOV de até 1:30).");
            }
            repo.save(m);
        });
    }

    private static void apagarPasta(Path pasta) {
        if (pasta == null) {
            return;
        }
        try (Stream<Path> arvore = Files.walk(pasta)) {
            arvore.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Pasta temporaria de video nao removida: {}", pasta);
        }
    }
}
