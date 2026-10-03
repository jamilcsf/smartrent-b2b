package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Promove, por tempo: REPUBLICACAO_AGENDADA vencida volta a PUBLICADO e
 * AGUARDANDO com a janela cumprida vira PRONTO_PARA_PUBLICAR. E uma segunda
 * linha de defesa: a consulta publica ja trata o anuncio vencido como visivel
 * e a publicacao revalida o prazo, entao um atraso do job nunca estende o
 * tempo fora do ar nem libera nada antes da hora.
 */
@Component
public class PromocaoAnuncioJob {

    private static final Logger log = LoggerFactory.getLogger(PromocaoAnuncioJob.class);

    private final AnuncioService anuncioService;

    public PromocaoAnuncioJob(AnuncioService anuncioService) {
        this.anuncioService = anuncioService;
    }

    @Scheduled(fixedDelayString = "${smartrent.jobs.promocao-intervalo-ms:60000}", initialDelay = 30_000)
    public void executar() {
        try {
            int promovidos = anuncioService.promoverVencidos();
            if (promovidos > 0) {
                log.info("{} anuncio(s) promovido(s) por tempo.", promovidos);
            }
        } catch (RuntimeException e) {
            log.error("Promocao de anuncios falhou; tentara de novo na proxima execucao.", e);
        }
    }
}
