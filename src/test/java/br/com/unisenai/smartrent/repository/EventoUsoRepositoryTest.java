package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.dto.AdminDtos.DiaTotal;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.AtividadeHora;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.CelulaCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ElementoClicado;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.PaginaVista;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** As agregacoes do painel rodam no banco (H2 aqui, PostgreSQL em producao), nao em memoria. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:telemetria;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class EventoUsoRepositoryTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 8);
    private static final Instant INSTANTE = Instant.parse("2026-10-08T15:00:00Z");

    @Autowired
    private EventoUsoRepository repository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    private void evento(TipoEventoUso tipo, String pagina, String alvo, String sessao, Dispositivo disp, Integer x, Integer y,
                        Integer valor, LocalDate dia, int hora) {
        repository.save(new EventoUso(tipo, pagina, alvo, sessao, "VISITANTE", disp, x, y, x == null ? null : 3000, valor,
                dia, hora, dia.getDayOfWeek().getValue(), INSTANTE));
    }

    @Test
    @DisplayName("CT1110 - Mapa de calor: cliques da mesma regiao caem na mesma celula; pagina e dispositivo separam os dados")
    void celulas() {
        evento(TipoEventoUso.CLIQUE, "/imoveis.html", "#a", "s1", Dispositivo.DESKTOP, 505, 130, null, HOJE, 10);
        evento(TipoEventoUso.CLIQUE, "/imoveis.html", "#a", "s2", Dispositivo.DESKTOP, 510, 140, null, HOJE, 10);
        evento(TipoEventoUso.CLIQUE, "/imoveis.html", "#b", "s3", Dispositivo.DESKTOP, 100, 900, null, HOJE, 10);
        evento(TipoEventoUso.CLIQUE, "/imoveis.html", "#a", "s4", Dispositivo.MOBILE, 505, 130, null, HOJE, 10);
        evento(TipoEventoUso.CLIQUE, "/login.html", "#a", "s5", Dispositivo.DESKTOP, 505, 130, null, HOJE, 10);

        List<CelulaCalor> celulas = repository.celulasDeCalor("/imoveis.html", Dispositivo.DESKTOP, HOJE.minusDays(6));
        assertEquals(2, celulas.size());
        CelulaCalor quente = celulas.stream().filter(c -> c.coluna() == 25).findFirst().orElseThrow();
        assertEquals(5, quente.linha());
        assertEquals(2, quente.cliques());

        List<ElementoClicado> elementos = repository.elementosDaPagina("/imoveis.html", Dispositivo.DESKTOP, HOJE.minusDays(6), PageRequest.of(0, 5));
        assertEquals("#a", elementos.get(0).alvo());
        assertEquals(2, elementos.get(0).cliques());
    }

    @Test
    @DisplayName("CT1111 - Periodo: evento anterior ao inicio da janela nao entra nas agregacoes")
    void periodo() {
        evento(TipoEventoUso.VISUALIZACAO, "/imoveis.html", null, "s1", Dispositivo.DESKTOP, null, null, null, HOJE, 9);
        evento(TipoEventoUso.VISUALIZACAO, "/imoveis.html", null, "s2", Dispositivo.DESKTOP, null, null, null, HOJE.minusDays(10), 9);
        assertEquals(1, repository.contar(TipoEventoUso.VISUALIZACAO, HOJE.minusDays(6)));
        assertEquals(2, repository.contar(TipoEventoUso.VISUALIZACAO, HOJE.minusDays(30)));
        assertEquals(1, repository.contarSessoes(HOJE.minusDays(6)));
    }

    @Test
    @DisplayName("CT1112 - Paginas mais vistas, serie diaria, atividade por hora, dispositivos e permanencia media")
    void resumo() {
        for (int i = 0; i < 3; i++) {
            evento(TipoEventoUso.VISUALIZACAO, "/imoveis.html", null, "s" + i, Dispositivo.DESKTOP, null, null, null, HOJE, 14);
        }
        evento(TipoEventoUso.VISUALIZACAO, "/imoveis.html", null, "s0", Dispositivo.DESKTOP, null, null, null, HOJE.minusDays(1), 20);
        evento(TipoEventoUso.VISUALIZACAO, "/imoveis/{id}", null, "s0", Dispositivo.MOBILE, null, null, null, HOJE, 14);
        evento(TipoEventoUso.PERMANENCIA, "/imoveis.html", null, "s0", Dispositivo.DESKTOP, null, null, 30, HOJE, 14);
        evento(TipoEventoUso.PERMANENCIA, "/imoveis.html", null, "s1", Dispositivo.DESKTOP, null, null, 50, HOJE, 14);
        LocalDate desde = HOJE.minusDays(6);

        List<PaginaVista> paginas = repository.paginasMaisVistas(desde, PageRequest.of(0, 5));
        assertEquals("/imoveis.html", paginas.get(0).pagina());
        assertEquals(4, paginas.get(0).visualizacoes());
        assertEquals(3, paginas.get(0).sessoes());

        assertEquals(2, repository.serieDiaria(desde).size());
        assertEquals(40.0, repository.permanenciaMedia(desde), 0.001);

        AtividadeHora pico = repository.atividadePorHora(desde).stream()
                .filter(a -> a.hora() == 14).findFirst().orElseThrow();
        assertEquals(4, pico.eventos(), "3 visualizacoes do catalogo + 1 do detalhe, as 14h");
        assertEquals(4, pico.diaSemana(), "quinta-feira");

        List<ContagemRotulo> disp = repository.sessoesPorDispositivo(desde);
        assertEquals("DESKTOP", disp.get(0).rotulo());
        assertEquals(1, repository.contarSessoesNaPagina(TipoEventoUso.VISUALIZACAO, "/imoveis/{id}", desde));
    }

    @Test
    @DisplayName("CT1113 - Exportacao por chave: le em lotes crescentes de id; retencao apaga so o que e mais antigo que o corte")
    void exportarERetencao() {
        for (int i = 0; i < 7; i++) {
            evento(TipoEventoUso.VISUALIZACAO, "/imoveis.html", null, "s" + i, Dispositivo.DESKTOP, null, null, null, HOJE, 9);
        }
        LocalDate desde = HOJE.minusDays(6);
        List<EventoUso> primeiro = repository.lote(0, desde, PageRequest.of(0, 5));
        assertEquals(5, primeiro.size());
        List<EventoUso> segundo = repository.lote(primeiro.get(4).getId(), desde, PageRequest.of(0, 5));
        assertEquals(2, segundo.size());
        assertTrue(segundo.get(0).getId() > primeiro.get(4).getId());

        assertEquals(0, repository.apagarAnterioresA(INSTANTE.minusSeconds(1)));
        assertEquals(7, repository.apagarAnterioresA(INSTANTE.plusSeconds(1)));
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("CT1114 - Cadastros por dia (agrupamento por data da coluna datetime) funciona no banco")
    void cadastrosPorDia() {
        for (int i = 0; i < 3; i++) {
            Usuario u = new Usuario();
            u.setNome("U" + i);
            u.setEmail("u" + i + "@t.dev");
            u.setSenhaHash("x".repeat(60));
            u.setPapel(PapelUsuario.CLIENTE);
            usuarioRepository.save(u);
        }
        List<DiaTotal> dias = usuarioRepository.cadastrosPorDia(LocalDateTime.now().minusDays(1));
        assertEquals(1, dias.size());
        assertEquals(3, dias.get(0).total());
        assertEquals(1, usuarioRepository.contagemPorPapel().size());
    }
}
