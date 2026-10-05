package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reprocessa, uma unica vez, as fotos de anuncio gravadas antes do saneamento (com EXIF/GPS do celular).
 *
 * <p>Idempotente: so toca em imagens com {@code metadados_removidos = false} e marca cada uma ao terminar, entao
 * rodar de novo nao recodifica (o que degradaria o JPEG). Uma foto que nao decodifica fica sem marca e e contada
 * como falha; as demais seguem. Registra apenas contagens.
 */
@Component
public class SaneamentoFotosAnuncio implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaneamentoFotosAnuncio.class);
    static final int TAMANHO_LOTE = 50;

    private final ImovelMidiaRepository midias;
    private final MidiaStorage storage;
    private final MidiaProcessador processador;
    private final TransactionTemplate transacao;
    private final boolean aoIniciar;

    public SaneamentoFotosAnuncio(ImovelMidiaRepository midias, MidiaStorage storage, MidiaProcessador processador,
                                  PlatformTransactionManager tm,
                                  @Value("${smartrent.midia.sanear-ao-iniciar:true}") boolean aoIniciar) {
        this.midias = midias;
        this.storage = storage;
        this.processador = processador;
        this.transacao = new TransactionTemplate(tm);
        this.aoIniciar = aoIniciar;
    }

    /** Fotos reprocessadas, falhas (ficam para a proxima execucao) e fotos sem arquivo local. */
    public record Resultado(long saneadas, long falhas, long semArquivo) {
        Resultado somar(Resultado o) {
            return new Resultado(saneadas + o.saneadas, falhas + o.falhas, semArquivo + o.semArquivo);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!aoIniciar) {
            return;
        }
        try {
            Resultado r = executar();
            if (r.saneadas() + r.falhas() + r.semArquivo() > 0) {
                log.info("Saneamento de fotos de anuncio: {} reprocessada(s), {} falha(s), {} sem arquivo local.",
                        r.saneadas(), r.falhas(), r.semArquivo());
            }
        } catch (RuntimeException e) {
            log.error("Saneamento de fotos de anuncio interrompido ({}); sera retomado na proxima execucao.",
                    e.getClass().getSimpleName());
        }
    }

    public Resultado executar() {
        Resultado total = new Resultado(0, 0, 0);
        long ultimoId = 0;
        while (true) {
            List<ImovelMidia> lote = midias.findImagensParaSanear(ultimoId, PageRequest.of(0, TAMANHO_LOTE));
            if (lote.isEmpty()) {
                return total;
            }
            for (ImovelMidia m : lote) {
                total = total.somar(sanear(m.getId()));
                ultimoId = m.getId();
            }
        }
    }

    private Resultado sanear(Long id) {
        Path temporario = null;
        Path miniatura = null;
        try {
            ImovelMidia m = midias.findById(id).orElse(null);
            if (m == null || m.isMetadadosRemovidos()) {
                return new Resultado(0, 0, 0);
            }
            Path local;
            try {
                local = storage.caminhoLocal(m.getArquivo());
            } catch (UnsupportedOperationException e) {
                return new Resultado(0, 0, 1);
            }
            if (local == null || !Files.isRegularFile(local)) {
                return new Resultado(0, 0, 1);
            }
            temporario = Files.createTempFile("smartrent-saneia-", ".tmp");
            Files.copy(local, temporario, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            MidiaProcessador.Inspecao atual = new MidiaProcessador.Inspecao(m.getMime(),
                    "image/png".equals(m.getMime()) ? "png" : "jpg", m.getLargura(), m.getAltura(), null);
            MidiaProcessador.Inspecao nova = processador.sanearImagem(temporario, atual);

            storage.salvar(m.getArquivo(), temporario);
            if (m.getMiniatura() != null) {
                miniatura = Files.createTempFile("smartrent-saneia-mini-", ".jpg");
                if (processador.gerarMiniatura(temporario, miniatura)) {
                    storage.salvar(m.getMiniatura(), miniatura);
                }
            }
            long tamanho = Files.size(temporario);
            transacao.executeWithoutResult(status -> midias.findById(id).ifPresent(x -> {
                x.setTamanhoBytes(tamanho);
                x.setLargura(nova.largura());
                x.setAltura(nova.altura());
                x.setMetadadosRemovidos(true);
                midias.save(x);
            }));
            return new Resultado(1, 0, 0);
        } catch (IOException | RuntimeException e) {
            return new Resultado(0, 1, 0); // sem detalhe: a mensagem do erro poderia citar o caminho do arquivo
        } finally {
            apagar(temporario);
            apagar(miniatura);
        }
    }

    private static void apagar(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignorado) {
                // arquivo temporario: o sistema operacional limpa depois
            }
        }
    }
}
