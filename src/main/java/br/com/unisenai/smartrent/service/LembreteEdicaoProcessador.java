package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.LembreteEdicao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.LembreteEdicaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Envia o lembrete de edicao esquecida de UM imovel, numa transacao propria.
 *
 * <p>Garantias:
 * <ul>
 *   <li><b>Somente leitura sobre o anuncio.</b> Nunca altera o imovel nem o
 *       rascunho: so grava lembretes e notificacoes.</li>
 *   <li><b>Idempotente.</b> Cada lembrete e a linha unica (imovel, edicao,
 *       numero, canal); se ja foi entregue, nao reenvia. O numero devido e
 *       calculado do tempo decorrido, entao um job atrasado envia o mais
 *       recente em vez de empilhar os perdidos.</li>
 *   <li><b>Falha isolada.</b> Erro ao notificar fica registrado na linha e e
 *       tentado de novo na proxima execucao; nada propaga para o imovel.</li>
 * </ul>
 */
@Service
public class LembreteEdicaoProcessador {

    public static final String CANAL_IN_APP = "IN_APP";

    private static final Logger log = LoggerFactory.getLogger(LembreteEdicaoProcessador.class);

    private final ImovelRepository imovelRepository;
    private final LembreteEdicaoRepository lembreteRepository;
    private final NotificacaoService notificacaoService;
    private final NotificadorEmail notificadorEmail;
    private final AnuncioProperties props;
    private final Clock clock;
    private final String urlBase;

    public LembreteEdicaoProcessador(ImovelRepository imovelRepository,
                                     LembreteEdicaoRepository lembreteRepository,
                                     NotificacaoService notificacaoService,
                                     NotificadorEmail notificadorEmail,
                                     AnuncioProperties props,
                                     Clock clock,
                                     @Value("${smartrent.app.url:http://localhost:8080}") String urlBase) {
        this.imovelRepository = imovelRepository;
        this.lembreteRepository = lembreteRepository;
        this.notificacaoService = notificacaoService;
        this.notificadorEmail = notificadorEmail;
        this.props = props;
        this.clock = clock;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * Numero do lembrete devido agora: 0 antes do primeiro prazo; depois,
     * 1 no primeiro, 2 um intervalo depois, e assim por diante, limitado ao
     * maximo configurado.
     */
    int numeroDevido(LocalDateTime edicaoIniciadaEm, LocalDateTime agora) {
        long decorridoH = Duration.between(edicaoIniciadaEm, agora).toHours();
        long apos = props.lembreteAposHoras();
        if (decorridoH < apos) {
            return 0;
        }
        long intervalo = props.lembreteIntervaloHoras();
        long extras = intervalo > 0 ? (decorridoH - apos) / intervalo : 0;
        return (int) Math.min(props.lembreteMax(), 1 + extras);
    }

    @Transactional
    public void processar(Long imovelId) {
        Optional<Imovel> achado = imovelRepository.findById(imovelId);
        if (achado.isEmpty()) {
            return;
        }
        Imovel imovel = achado.get();
        // Saiu de edicao (confirmou ou descartou): os lembretes param aqui.
        if (imovel.getStatus() != StatusAnuncio.EM_EDICAO || imovel.getEdicaoIniciadaEm() == null) {
            return;
        }

        LocalDateTime agora = Agora.de(clock);
        int numero = numeroDevido(imovel.getEdicaoIniciadaEm(), agora);
        if (numero == 0) {
            return;
        }

        Usuario gestor = imovel.getUsuario();
        Duration emEdicao = Duration.between(imovel.getEdicaoIniciadaEm(), agora);
        String titulo = "Anúncio fora do ar há " + descrever(emEdicao);
        String link = "/dashboard.html?imovel=" + imovel.getId();
        String mensagem = "O anúncio \"" + imovel.getTitulo() + "\" está em edição há " + descrever(emEdicao)
                + ", fora do catálogo e sem receber novas reservas. Abra o dashboard para "
                + "\"Confirmar alteração\" ou \"Descartar edição\".";

        entregar(imovel, numero, CANAL_IN_APP, agora,
                () -> notificacaoService.criar(gestor, imovel.getId(), titulo, mensagem, link));
        entregar(imovel, numero, notificadorEmail.canal(), agora,
                () -> notificadorEmail.enviar(gestor, titulo, mensagem + " " + urlBase + link));
    }

    private void entregar(Imovel imovel, int numero, String canal, LocalDateTime agora, Runnable envio) {
        LembreteEdicao l = lembreteRepository
                .findByImovelIdAndEdicaoIniciadaEmAndNumeroAndCanal(
                        imovel.getId(), imovel.getEdicaoIniciadaEm(), numero, canal)
                .orElse(null);
        if (l == null) {
            l = new LembreteEdicao();
            l.setImovel(imovel);
            l.setEdicaoIniciadaEm(imovel.getEdicaoIniciadaEm());
            l.setNumero(numero);
            l.setCanal(canal);
            l.setCriadoEm(agora);
            // Flush antes de enviar: se outra execucao criou a mesma linha, a chave
            // unica falha aqui, antes de qualquer envio, e nao ha duplicidade.
            l = lembreteRepository.saveAndFlush(l);
        }
        if (l.getEnviadoEm() != null) {
            return; // ja entregue em execucao anterior
        }

        l.setTentativas(l.getTentativas() + 1);
        try {
            envio.run();
            l.setEnviadoEm(agora);
            l.setErro(null);
        } catch (RuntimeException e) {
            String motivo = e.getClass().getSimpleName() + ": " + e.getMessage();
            l.setErro(motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
            log.warn("Falha ao enviar lembrete {} ({}) do imovel {}: {}",
                    numero, canal, imovel.getId(), motivo);
        }
        lembreteRepository.save(l);
    }

    static String descrever(Duration d) {
        long horas = d.toHours();
        if (horas < 48) {
            return horas + (horas == 1 ? " hora" : " horas");
        }
        long dias = horas / 24;
        return dias + " dias";
    }
}
