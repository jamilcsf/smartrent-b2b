package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.EventoRequest;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteRequest;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteResponse;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import br.com.unisenai.smartrent.repository.EventoUsoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A coleta e publica: nada do que chega e confiado. Estes testes fixam as regras de validacao, de privacidade
 * (so estrutura, nunca texto) e de limite (ADR-009).
 */
class TelemetriaServiceTest {

    // Quarta-feira, 2026-10-07 22:30 em Brasilia (01:30 UTC de quinta): o dia e a hora devem seguir o fuso da plataforma.
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-08T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));

    private EventoUsoRepository repository;
    private TelemetriaService service;

    @BeforeEach
    void preparar() {
        repository = mock(EventoUsoRepository.class);
        service = new TelemetriaService(repository, new LimitadorDeTaxa(RELOGIO), RELOGIO, true, 3, 180);
    }

    private static EventoRequest clique(Integer x, Integer y, String alvo) {
        return new EventoRequest("CLIQUE", "/imoveis.html", alvo, x, y, 3000, null);
    }

    private List<EventoUso> gravados() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<EventoUso>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(c.capture());
        List<EventoUso> lista = new ArrayList<>();
        c.getValue().forEach(lista::add);
        return lista;
    }

    @Test
    @DisplayName("CT1103 - Evento valido grava a grade do mapa, o dia e a hora no fuso da plataforma e o papel; relogio do cliente nao entra")
    void gravaEvento() {
        LoteResponse r = service.registrar(new LoteRequest("sessao-1234-abcd", "mobile", List.of(clique(505, 130, "#btnReservar"))),
                "CLIENTE", "1.2.3.4");
        assertEquals(1, r.aceitos());
        EventoUso e = gravados().get(0);
        assertEquals(TipoEventoUso.CLIQUE, e.getTipo());
        assertEquals(Dispositivo.MOBILE, e.getDispositivo());
        assertEquals("CLIENTE", e.getPapel());
        assertEquals(25, e.getXCelula(), "505 / 20");
        assertEquals(5, e.getYCelula(), "130 / 25");
        assertEquals("2026-10-07", e.getDia().toString());
        assertEquals(22, e.getHora());
        assertEquals(3, e.getDiaSemana(), "quarta-feira = 3");
        assertEquals("#btnReservar", e.getAlvo());
    }

    @Test
    @DisplayName("CT1104 - Pagina normalizada: query e fragmento saem, id de imovel vira {id}, API e painel de admin nao sao medidos")
    void normalizaPagina() {
        assertEquals("/imoveis/{id}", TelemetriaService.normalizarPagina("/imoveis/123?token=abc#x"));
        assertEquals("/imoveis.html", TelemetriaService.normalizarPagina("/imoveis.html?busca=praia"));
        assertEquals("/login.html", TelemetriaService.normalizarPagina("/login.html/"));
        assertNull(TelemetriaService.normalizarPagina("/admin.html"));
        assertNull(TelemetriaService.normalizarPagina("/api/auth/me"));
        assertNull(TelemetriaService.normalizarPagina("javascript:alert(1)"));
        assertNull(TelemetriaService.normalizarPagina("/a b"));
        assertNull(TelemetriaService.normalizarPagina("/" + "x".repeat(100)));
        assertNull(TelemetriaService.normalizarPagina(null));
    }

    @Test
    @DisplayName("CT1105 - Alvo so aceita estrutura: caracteres de texto livre saem, comeco de formula (=, +, @, -) e cortado, tamanho limitado")
    void normalizaAlvo() {
        assertEquals("a[href=/imoveis/{id}]", TelemetriaService.normalizarAlvo("a[href=/imoveis/42]"));
        assertEquals("button", TelemetriaService.normalizarAlvo("button"));
        assertEquals("cmd", TelemetriaService.normalizarAlvo("=cmd"));
        assertEquals("SUM1", TelemetriaService.normalizarAlvo("+SUM(1)"));
        assertEquals("imgsrc=xonerror=alert1", TelemetriaService.normalizarAlvo("<img src=x onerror=alert(1)>"),
                "sem < > ( ) nem espaco, o texto nao vira marcacao");
        assertNull(TelemetriaService.normalizarAlvo("   "));
        assertNull(TelemetriaService.normalizarAlvo("!!!"));
        assertEquals(100, TelemetriaService.normalizarAlvo("a".repeat(500)).length());
    }

    @Test
    @DisplayName("CT1106 - Evento invalido e descartado sem derrubar o lote: tipo desconhecido, clique fora da pagina, rolagem > 100")
    void descartaInvalidos() {
        List<EventoRequest> eventos = List.of(
                new EventoRequest("INVENTADO", "/imoveis.html", null, null, null, null, null),
                clique(1001, 10, "#a"),
                clique(10, -1, "#a"),
                new EventoRequest("CLIQUE", "/api/x", "#a", 10, 10, 100, null),
                new EventoRequest("ROLAGEM", "/imoveis.html", null, null, null, null, 101),
                new EventoRequest("ROLAGEM", "/imoveis.html", null, null, null, null, 60),
                new EventoRequest("PERMANENCIA", "/imoveis.html", null, null, null, null, 99_999),
                new EventoRequest("PERMANENCIA", "/imoveis.html", null, null, null, null, 7_200));
        LoteResponse r = service.registrar(new LoteRequest("sessao-1234-abcd", "DESKTOP", eventos), "VISITANTE", "ip");
        assertEquals(2, r.aceitos());
        assertEquals(6, r.descartados());
        List<EventoUso> g = gravados();
        assertEquals(60, g.get(0).getValor());
        assertEquals(3600, g.get(1).getValor(), "aba esquecida aberta e limitada a 1 h");
    }

    @Test
    @DisplayName("CT1107 - Sessao invalida, lote vazio ou coleta desligada: nada e gravado")
    void naoGrava() {
        assertEquals(0, service.registrar(new LoteRequest("ab", "DESKTOP", List.of(clique(1, 1, "#a"))), "VISITANTE", "ip").aceitos());
        assertEquals(0, service.registrar(new LoteRequest("sessao com espaco!", "DESKTOP", List.of(clique(1, 1, "#a"))), "VISITANTE", "ip").aceitos());
        assertEquals(0, service.registrar(new LoteRequest("sessao-1234-abcd", "DESKTOP", List.of()), "VISITANTE", "ip").aceitos());
        assertEquals(0, service.registrar(null, "VISITANTE", "ip").aceitos());
        TelemetriaService desligada = new TelemetriaService(repository, new LimitadorDeTaxa(RELOGIO), RELOGIO, false, 3, 180);
        assertEquals(0, desligada.registrar(new LoteRequest("sessao-1234-abcd", "DESKTOP", List.of(clique(1, 1, "#a"))), "VISITANTE", "ip").aceitos());
        verify(repository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("CT1108 - Lote com mais de 50 eventos so grava os 50 primeiros; excesso conta como descartado")
    void limitaTamanhoDoLote() {
        List<EventoRequest> muitos = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            muitos.add(clique(10, 10, "#a"));
        }
        LoteResponse r = service.registrar(new LoteRequest("sessao-1234-abcd", "DESKTOP", muitos), "VISITANTE", "ip");
        assertEquals(50, r.aceitos());
        assertEquals(30, r.descartados());
    }

    @Test
    @DisplayName("CT1109 - Limite por origem: acima de N lotes por minuto o servidor descarta, sem erro para o navegador")
    void limitaFrequencia() {
        LoteRequest lote = new LoteRequest("sessao-1234-abcd", "DESKTOP", List.of(clique(10, 10, "#a")));
        for (int i = 0; i < 3; i++) {
            assertEquals(1, service.registrar(lote, "VISITANTE", "9.9.9.9").aceitos());
        }
        LoteResponse r = service.registrar(lote, "VISITANTE", "9.9.9.9");
        assertEquals(0, r.aceitos());
        assertEquals(1, r.descartados());
        assertEquals(1, service.registrar(lote, "VISITANTE", "8.8.8.8").aceitos(), "outra origem nao e afetada");
    }
}
