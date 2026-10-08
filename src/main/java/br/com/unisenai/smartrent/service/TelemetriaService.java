package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.EventoRequest;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteRequest;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteResponse;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import br.com.unisenai.smartrent.repository.EventoUsoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Coleta da telemetria de uso (ADR-009). O endpoint e publico, entao NADA do que chega e confiado: o lote e limitado
 * em tamanho e em frequencia, cada campo e validado e normalizado, a hora vem do relogio do servidor e o que nao
 * passa na validacao e descartado em silencio (a coleta nunca pode quebrar a navegacao). Nao se grava usuario, IP
 * nem texto de elemento: so o papel de quem navegou e um sorteio de sessao gerado pelo proprio navegador.
 */
@Service
public class TelemetriaService {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetriaService.class);

    static final int MAX_EVENTOS_POR_LOTE = 50;
    private static final Pattern SESSAO = Pattern.compile("^[A-Za-z0-9-]{8,36}$");
    private static final Pattern PAGINA = Pattern.compile("^/[A-Za-z0-9_./{}-]{0,78}$");
    private static final Pattern ID_NUMERICO = Pattern.compile("/imoveis/\\d+");
    private static final Pattern ALVO_PROIBIDO = Pattern.compile("[^A-Za-z0-9_#.\\-/\\[\\]=:@{}]");
    /** O painel nao se observa a si mesmo, e a API nao e pagina. */
    private static final Pattern IGNORADAS = Pattern.compile("^/(admin\\.html|moderacao\\.html|api/.*)$");

    private final EventoUsoRepository repository;
    private final LimitadorDeTaxa limitador;
    private final Clock clock;
    private final boolean habilitada;
    private final int lotesPorMinuto;
    private final int retencaoDias;

    public TelemetriaService(EventoUsoRepository repository, LimitadorDeTaxa limitador, Clock clock,
                             @Value("${smartrent.telemetria.habilitada:true}") boolean habilitada,
                             @Value("${smartrent.telemetria.lotes-por-minuto:60}") int lotesPorMinuto,
                             @Value("${smartrent.telemetria.retencao-dias:180}") int retencaoDias) {
        this.repository = repository;
        this.limitador = limitador;
        this.clock = clock;
        this.habilitada = habilitada;
        this.lotesPorMinuto = lotesPorMinuto;
        this.retencaoDias = retencaoDias;
    }

    public boolean habilitada() {
        return habilitada;
    }

    /**
     * @param papel   papel de quem navega ("VISITANTE" quando nao ha login)
     * @param origem  chave de limite (IP de quem envia); usada so em memoria, nunca gravada
     */
    @Transactional
    public LoteResponse registrar(LoteRequest lote, String papel, String origem) {
        if (!habilitada || lote == null || lote.eventos() == null || lote.eventos().isEmpty()) {
            return new LoteResponse(0, 0);
        }
        if (lote.sessaoId() == null || !SESSAO.matcher(lote.sessaoId()).matches()) {
            return new LoteResponse(0, lote.eventos().size());
        }
        if (!limitador.permitir("telemetria:" + origem, lotesPorMinuto, Duration.ofMinutes(1))) {
            return new LoteResponse(0, lote.eventos().size());
        }

        Dispositivo dispositivo = dispositivo(lote.dispositivo());
        ZonedDateTime agora = ZonedDateTime.now(clock);
        Instant instante = agora.toInstant();
        List<EventoUso> validos = new ArrayList<>();
        int descartados = Math.max(0, lote.eventos().size() - MAX_EVENTOS_POR_LOTE);
        for (EventoRequest e : lote.eventos().stream().limit(MAX_EVENTOS_POR_LOTE).toList()) {
            EventoUso evento = converter(e, lote.sessaoId(), papel, dispositivo, agora, instante);
            if (evento == null) {
                descartados++;
            } else {
                validos.add(evento);
            }
        }
        repository.saveAll(validos);
        return new LoteResponse(validos.size(), descartados);
    }

    private EventoUso converter(EventoRequest e, String sessaoId, String papel, Dispositivo dispositivo,
                                ZonedDateTime agora, Instant instante) {
        if (e == null) {
            return null;
        }
        TipoEventoUso tipo = tipo(e.tipo());
        String pagina = normalizarPagina(e.pagina());
        if (tipo == null || pagina == null) {
            return null;
        }
        Integer x = null;
        Integer y = null;
        Integer altura = null;
        Integer valor = null;
        String alvo = null;
        switch (tipo) {
            case CLIQUE -> {
                if (!entre(e.x(), 0, 1000) || !entre(e.y(), 0, 200_000) || !entre(e.altura(), 0, 500_000)) {
                    return null;
                }
                x = e.x();
                y = e.y();
                altura = e.altura();
                alvo = normalizarAlvo(e.alvo());
            }
            case VISUALIZACAO -> altura = entre(e.altura(), 0, 500_000) ? e.altura() : null;
            case ROLAGEM -> {
                if (!entre(e.valor(), 0, 100)) {
                    return null;
                }
                valor = e.valor();
            }
            case PERMANENCIA -> {
                if (!entre(e.valor(), 0, 86_400)) {
                    return null;
                }
                valor = Math.min(e.valor(), 3600); // aba esquecida aberta nao distorce a media
            }
        }
        return new EventoUso(tipo, pagina, alvo, sessaoId, papel, dispositivo, x, y, altura, valor,
                agora.toLocalDate(), agora.getHour(), agora.getDayOfWeek().getValue(), instante);
    }

    /** Caminho sem query/fragmento, com ids de imovel trocados por {id}; nulo quando nao e uma pagina a medir. */
    static String normalizarPagina(String bruta) {
        if (bruta == null) {
            return null;
        }
        String p = bruta;
        int corte = indiceDe(p, '?', '#');
        if (corte >= 0) {
            p = p.substring(0, corte);
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        p = ID_NUMERICO.matcher(p).replaceAll("/imoveis/{id}");
        if (!PAGINA.matcher(p).matches() || IGNORADAS.matcher(p).matches()) {
            return null;
        }
        return p;
    }

    /** Alvo e so um identificador estrutural (#id, [data-track], tag[href]); texto livre nao e aceito. */
    static String normalizarAlvo(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String a = ID_NUMERICO.matcher(bruto.trim()).replaceAll("/imoveis/{id}");
        a = ALVO_PROIBIDO.matcher(a).replaceAll("");
        // Planilhas tratam "=", "+", "-", "@" no inicio como formula: o alvo sempre comeca por letra, # , [ ou .
        a = a.replaceFirst("^[^A-Za-z#\\[.]+", "");
        if (a.length() > 100) {
            a = a.substring(0, 100);
        }
        return a.isEmpty() ? null : a;
    }

    private static TipoEventoUso tipo(String t) {
        try {
            return t == null ? null : TipoEventoUso.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Dispositivo dispositivo(String d) {
        try {
            return d == null ? Dispositivo.DESKTOP : Dispositivo.valueOf(d.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Dispositivo.DESKTOP;
        }
    }

    private static boolean entre(Integer v, int min, int max) {
        return v != null && v >= min && v <= max;
    }

    private static int indiceDe(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        return ib < 0 ? ia : Math.min(ia, ib);
    }

    /** Retencao: eventos mais velhos que o prazo sao apagados todo dia (minimizacao de dados). */
    @Scheduled(cron = "${smartrent.telemetria.cron-retencao:0 30 3 * * *}")
    @Transactional
    public void aplicarRetencao() {
        if (retencaoDias <= 0) {
            return;
        }
        int apagados = repository.apagarAnterioresA(clock.instant().minus(Duration.ofDays(retencaoDias)));
        if (apagados > 0) {
            LOG.info("Telemetria: {} evento(s) com mais de {} dias apagado(s).", apagados, retencaoDias);
        }
    }
}
