package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.CelulaCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ElementoClicado;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.MapaDeCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ResumoUso;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import br.com.unisenai.smartrent.repository.EventoUsoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura analitica da telemetria para o painel de admin: resumo geral, mapa de calor por pagina e dispositivo e
 * exportacao dos eventos brutos (CSV ou NDJSON) para ferramentas externas de big data (BI, Spark, DuckDB...).
 * Tudo e agregado no banco; a exportacao anda por chave em lotes, sem carregar tudo na memoria.
 */
@Service
public class TelemetriaAnaliseService {

    public static final int DIAS_PADRAO = 30;
    public static final int DIAS_MAX = 365;
    static final int TAMANHO_LOTE_EXPORTACAO = 5_000;
    static final String PAGINA_DETALHE = "/imoveis/{id}";

    private final EventoUsoRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TelemetriaAnaliseService(EventoUsoRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    static int dias(Integer dias) {
        if (dias == null) {
            return DIAS_PADRAO;
        }
        return Math.max(1, Math.min(dias, DIAS_MAX));
    }

    private LocalDate desde(int dias) {
        return LocalDate.now(clock).minusDays(dias - 1L);
    }

    @Transactional(readOnly = true)
    public ResumoUso resumo(Integer diasPedidos) {
        int dias = dias(diasPedidos);
        LocalDate desde = desde(dias);
        long visualizacoes = repository.contar(TipoEventoUso.VISUALIZACAO, desde);
        long cliques = repository.contar(TipoEventoUso.CLIQUE, desde);
        long sessoes = repository.contarSessoes(desde);
        Double permanencia = repository.permanenciaMedia(desde);
        return new ResumoUso(dias, visualizacoes, cliques, sessoes,
                permanencia == null ? 0 : permanencia,
                sessoes == 0 ? 0 : (double) cliques / sessoes,
                repository.serieDiaria(desde),
                repository.paginasMaisVistas(desde, PageRequest.of(0, 15)),
                repository.elementosMaisClicados(desde, PageRequest.of(0, 20)),
                repository.atividadePorHora(desde),
                repository.sessoesPorDispositivo(desde),
                repository.sessoesPorPapel(desde),
                repository.permanenciaPorPagina(desde, PageRequest.of(0, 10)),
                repository.count());
    }

    @Transactional(readOnly = true)
    public List<String> paginas(Integer diasPedidos) {
        return repository.paginasComEventos(desde(dias(diasPedidos)), PageRequest.of(0, 50));
    }

    /** Sessoes que abriram um anuncio no periodo: base da taxa de conversao (reservas criadas / sessoes). */
    @Transactional(readOnly = true)
    public long sessoesNoDetalhe(Integer diasPedidos) {
        return repository.contarSessoesNaPagina(TipoEventoUso.VISUALIZACAO, PAGINA_DETALHE, desde(dias(diasPedidos)));
    }

    @Transactional(readOnly = true)
    public MapaDeCalor mapa(String pagina, String dispositivoPedido, Integer diasPedidos) {
        String normalizada = TelemetriaService.normalizarPagina(pagina);
        if (normalizada == null) {
            throw new IllegalArgumentException("Pagina invalida.");
        }
        Dispositivo dispositivo;
        try {
            dispositivo = Dispositivo.valueOf(dispositivoPedido == null ? "DESKTOP" : dispositivoPedido.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dispositivo invalido.");
        }
        int dias = dias(diasPedidos);
        LocalDate desde = desde(dias);

        List<CelulaCalor> celulas = repository.celulasDeCalor(normalizada, dispositivo, desde);
        long total = celulas.stream().mapToLong(CelulaCalor::cliques).sum();
        long maximo = celulas.stream().mapToLong(CelulaCalor::cliques).max().orElse(0);
        Double altura = repository.alturaMediaDoDocumento(normalizada, dispositivo, desde);
        int alturaDoc = altura == null ? 0 : (int) Math.round(altura);
        List<ElementoClicado> elementos = repository.elementosDaPagina(normalizada, dispositivo, desde, PageRequest.of(0, 15));
        List<ContagemRotulo> rolagem = repository.profundidadeRolagem(normalizada, dispositivo, desde);
        return new MapaDeCalor(normalizada, dispositivo.name(), dias, total, maximo,
                1000 / EventoUso.CELULA_X_PM, EventoUso.CELULA_Y_PX, alturaDoc, celulas, elementos, rolagem,
                repository.visualizacoesDaPagina(normalizada, dispositivo, desde));
    }

    // ---- Exportacao ----

    private static final String[] COLUNAS = {"id", "ocorrido_em", "dia", "hora", "dia_semana", "tipo", "pagina", "alvo",
            "sessao_id", "papel", "dispositivo", "x_pm", "y_px", "doc_altura", "x_celula", "y_celula", "valor"};

    /** Escreve os eventos do periodo, em lotes por chave. {@code csv=false} gera NDJSON (um JSON por linha). */
    public void exportar(OutputStream saida, Integer diasPedidos, boolean csv) throws IOException {
        LocalDate desde = desde(dias(diasPedidos));
        Writer w = new OutputStreamWriter(saida, StandardCharsets.UTF_8);
        if (csv) {
            w.write(String.join(",", COLUNAS));
            w.write('\n');
        }
        long ultimo = 0;
        while (true) {
            List<EventoUso> lote = repository.lote(ultimo, desde, PageRequest.of(0, TAMANHO_LOTE_EXPORTACAO));
            for (EventoUso e : lote) {
                w.write(csv ? linhaCsv(e) : linhaJson(e));
                w.write('\n');
            }
            w.flush();
            if (lote.size() < TAMANHO_LOTE_EXPORTACAO) {
                break;
            }
            ultimo = lote.get(lote.size() - 1).getId();
        }
    }

    private String linhaJson(EventoUso e) throws IOException {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", e.getId());
        n.put("ocorrido_em", e.getOcorridoEm().toString());
        n.put("dia", e.getDia().toString());
        n.put("hora", e.getHora());
        n.put("dia_semana", e.getDiaSemana());
        n.put("tipo", e.getTipo().name());
        n.put("pagina", e.getPagina());
        n.put("alvo", e.getAlvo());
        n.put("sessao_id", e.getSessaoId());
        n.put("papel", e.getPapel());
        n.put("dispositivo", e.getDispositivo().name());
        n.put("x_pm", e.getXPm());
        n.put("y_px", e.getYPx());
        n.put("doc_altura", e.getDocAltura());
        n.put("x_celula", e.getXCelula());
        n.put("y_celula", e.getYCelula());
        n.put("valor", e.getValor());
        return mapper.writeValueAsString(n);
    }

    private static String linhaCsv(EventoUso e) {
        String[] campos = {String.valueOf(e.getId()), e.getOcorridoEm().toString(), e.getDia().toString(),
                String.valueOf(e.getHora()), String.valueOf(e.getDiaSemana()), e.getTipo().name(), e.getPagina(),
                e.getAlvo(), e.getSessaoId(), e.getPapel(), e.getDispositivo().name(), texto(e.getXPm()),
                texto(e.getYPx()), texto(e.getDocAltura()), texto(e.getXCelula()), texto(e.getYCelula()),
                texto(e.getValor())};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapar(campos[i]));
        }
        return sb.toString();
    }

    private static String texto(Integer v) {
        return v == null ? "" : v.toString();
    }

    private static String escapar(String v) {
        if (v == null) {
            return "";
        }
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
