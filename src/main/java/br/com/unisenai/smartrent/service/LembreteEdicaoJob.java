package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varre os imoveis em EM_EDICAO e dispara lembretes. E apenas informativo:
 * nao altera estado, nao apaga rascunho, nao republica nem descarta nada. Cada
 * imovel e processado isoladamente, entao a falha de um nao trava os demais.
 */
@Component
public class LembreteEdicaoJob {

    private static final Logger log = LoggerFactory.getLogger(LembreteEdicaoJob.class);

    private final ImovelRepository imovelRepository;
    private final LembreteEdicaoProcessador processador;

    public LembreteEdicaoJob(ImovelRepository imovelRepository, LembreteEdicaoProcessador processador) {
        this.imovelRepository = imovelRepository;
        this.processador = processador;
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.lembrete-intervalo-ms:600000}", initialDelay = 60_000)
    public void executar() {
        for (Imovel imovel : imovelRepository.findByStatus(StatusAnuncio.EM_EDICAO)) {
            try {
                processador.processar(imovel.getId());
            } catch (RuntimeException e) {
                log.error("Lembrete de edicao falhou para o imovel {}; tentara de novo na proxima execucao.",
                        imovel.getId(), e);
            }
        }
    }
}
