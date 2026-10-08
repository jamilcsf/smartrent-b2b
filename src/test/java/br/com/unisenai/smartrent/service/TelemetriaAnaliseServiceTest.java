package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.CelulaCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import br.com.unisenai.smartrent.repository.EventoUsoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Leitura analitica da telemetria: periodo limitado, mapa de calor validado e exportacao CSV/NDJSON sem vazar formula. */
class TelemetriaAnaliseServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 8);

    private EventoUsoRepository repository;
    private TelemetriaAnaliseService service;

    @BeforeEach
    void preparar() {
        repository = mock(EventoUsoRepository.class);
        service = new TelemetriaAnaliseService(repository, new ObjectMapper(), RELOGIO);
    }

    @Test
    @DisplayName("CT1141 - Periodo: vazio vale 30 dias; fora da faixa e limitado a 1..365")
    void periodo() {
        assertEquals(30, TelemetriaAnaliseService.dias(null));
        assertEquals(1, TelemetriaAnaliseService.dias(-5));
        assertEquals(365, TelemetriaAnaliseService.dias(99_999));
        assertEquals(7, TelemetriaAnaliseService.dias(7));
    }

    @Test
    @DisplayName("CT1142 - Resumo: monta os totais do periodo, cliques por sessao e media de permanencia; sem sessoes nao divide por zero")
    void resumo() {
        when(repository.contar(TipoEventoUso.VISUALIZACAO, HOJE.minusDays(6))).thenReturn(100L);
        when(repository.contar(TipoEventoUso.CLIQUE, HOJE.minusDays(6))).thenReturn(50L);
        when(repository.contarSessoes(HOJE.minusDays(6))).thenReturn(20L);
        when(repository.permanenciaMedia(HOJE.minusDays(6))).thenReturn(42.0);
        when(repository.count()).thenReturn(1234L);
        var r = service.resumo(7);
        assertEquals(7, r.dias());
        assertEquals(100, r.visualizacoes());
        assertEquals(2.5, r.cliquesPorSessao(), 0.0001);
        assertEquals(42.0, r.segundosMediosNaPagina(), 0.0001);
        assertEquals(1234, r.eventosArmazenados());

        var vazio = service.resumo(1);
        assertEquals(0, vazio.cliquesPorSessao());
        assertEquals(0, vazio.segundosMediosNaPagina());
    }

    @Test
    @DisplayName("CT1143 - Mapa de calor: pagina e dispositivo invalidos sao recusados; o maximo da celula e a altura saem da agregacao")
    void mapa() {
        assertThrows(IllegalArgumentException.class, () -> service.mapa("/api/x", "DESKTOP", 30));
        assertThrows(IllegalArgumentException.class, () -> service.mapa("javascript:alert(1)", "DESKTOP", 30));
        assertThrows(IllegalArgumentException.class, () -> service.mapa("/imoveis.html", "GELADEIRA", 30));

        when(repository.celulasDeCalor(eq("/imoveis/{id}"), eq(Dispositivo.MOBILE), any()))
                .thenReturn(List.of(new CelulaCalor(3, 4, 7), new CelulaCalor(5, 6, 2)));
        when(repository.alturaMediaDoDocumento(eq("/imoveis/{id}"), eq(Dispositivo.MOBILE), any())).thenReturn(1999.6);
        when(repository.profundidadeRolagem(any(), any(), any())).thenReturn(List.of(new ContagemRotulo("50", 3)));
        var m = service.mapa("/imoveis/55?x=1", "mobile", 14);
        assertEquals("/imoveis/{id}", m.pagina());
        assertEquals(9, m.totalCliques());
        assertEquals(7, m.maximoCelula());
        assertEquals(2000, m.alturaDocumentoPx());
        assertEquals(50, m.colunas());
        assertEquals(14, m.dias());
        assertEquals("MOBILE", m.dispositivo());

        assertEquals("DESKTOP", service.mapa("/imoveis.html", null, null).dispositivo());
        assertEquals(0, service.mapa("/imoveis.html", null, null).alturaDocumentoPx());
    }

    @Test
    @DisplayName("CT1144 - Paginas e sessoes no detalhe: delegam ao repositorio com o periodo certo")
    void listas() {
        when(repository.paginasComEventos(eq(HOJE.minusDays(364)), any())).thenReturn(List.of("/imoveis.html"));
        assertEquals(List.of("/imoveis.html"), service.paginas(365));
        when(repository.contarSessoesNaPagina(TipoEventoUso.VISUALIZACAO, "/imoveis/{id}", HOJE.minusDays(29))).thenReturn(9L);
        assertEquals(9, service.sessoesNoDetalhe(null));
        verify(repository).contarSessoesNaPagina(TipoEventoUso.VISUALIZACAO, "/imoveis/{id}", HOJE.minusDays(29));
    }

    private static EventoUso clique(String alvo, String pagina) {
        return new EventoUso(TipoEventoUso.CLIQUE, pagina, alvo, "sessao-12345678", "VISITANTE", Dispositivo.DESKTOP, 500, 130, 2000, null,
                HOJE, 10, 4, Instant.parse("2026-10-08T13:00:00Z"));
    }

    @Test
    @DisplayName("CT1145 - Exportacao CSV: cabecalho, uma linha por evento e valores com virgula ou aspas protegidos")
    void exportaCsv() throws Exception {
        when(repository.lote(anyLong(), any(), any())).thenReturn(List.of(clique("a[href=/x,y]", "/imoveis.html"), clique(null, "/login.html")));
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        service.exportar(saida, 7, true);
        String[] linhas = saida.toString(StandardCharsets.UTF_8).split("\n");
        assertEquals(3, linhas.length);
        assertTrue(linhas[0].startsWith("id,ocorrido_em,dia,hora,dia_semana,tipo,pagina,alvo"));
        assertTrue(linhas[1].contains("CLIQUE,/imoveis.html,\"a[href=/x,y]\",sessao-12345678,VISITANTE,DESKTOP,500,130,2000,25,5,"));
        assertTrue(linhas[2].contains("CLIQUE,/login.html,,sessao-12345678"), "alvo ausente vira campo vazio");
    }

    @Test
    @DisplayName("CT1146 - Exportacao NDJSON: um JSON por linha, com nulos explicitos e sem cabecalho")
    void exportaNdjson() throws Exception {
        when(repository.lote(anyLong(), any(), any())).thenReturn(List.of(clique("#btn", "/imoveis.html")));
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        service.exportar(saida, null, false);
        String[] linhas = saida.toString(StandardCharsets.UTF_8).split("\n");
        assertEquals(1, linhas.length);
        var json = new ObjectMapper().readTree(linhas[0]);
        assertEquals("CLIQUE", json.get("tipo").asText());
        assertEquals("#btn", json.get("alvo").asText());
        assertEquals(25, json.get("x_celula").asInt());
        assertTrue(json.get("valor").isNull());
    }
}
