package br.com.unisenai.smartrent;

import br.com.unisenai.smartrent.config.ClockConfig;
import br.com.unisenai.smartrent.config.PlataformaTempo;
import br.com.unisenai.smartrent.controller.PaginasController;
import br.com.unisenai.smartrent.dto.CalendarioDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renomeacao Portal -> Dashboard, botao "Criar anuncio" so no Painel, e fuso horario (Brasilia). */
class PaginasEFusoTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static List<Path> arquivos(Path raiz, String... extensoes) throws IOException {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> java.util.Arrays.stream(extensoes).anyMatch(e -> p.getFileName().toString().endsWith(e)))
                    .toList();
        }
    }

    private static String relativo(Path p) {
        return STATIC.relativize(p).toString().replace('\\', '/');
    }

    // ------------------------------------------------------- renomeacao

    @Test
    @DisplayName("CT810 - Nenhum 'Portal da Transparencia' resta nas paginas, scripts e codigo: agora e 'Dashboard'")
    void semPortalDaTransparencia() throws IOException {
        Pattern p = Pattern.compile("portal\\s+da\\s+transpar[eê]ncia", Pattern.CASE_INSENSITIVE);
        List<String> achados = new ArrayList<>();
        for (Path raiz : List.of(STATIC, Path.of("src/main/java"))) {
            for (Path f : arquivos(raiz, ".html", ".js", ".java")) {
                if (p.matcher(Files.readString(f)).find()) {
                    achados.add(f.toString());
                }
            }
        }
        assertTrue(achados.isEmpty(), "ainda ha 'Portal da Transparencia': " + achados);
        assertTrue(Files.readString(STATIC.resolve("js/navegacao.js")).contains("texto: 'Dashboard'"));
        assertTrue(Files.readString(STATIC.resolve("estatisticas.html")).contains("<title>SmartRent B2B - Dashboard</title>"));
    }

    @Test
    @DisplayName("CT811 - Rota antiga redireciona para o Dashboard (estatisticas.html); index.html tambem leva ao Dashboard")
    void rotaAntigaRedireciona() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaginasController()).build();
        for (String rota : List.of("/transparencia", "/transparencia.html", "/portal-da-transparencia")) {
            mvc.perform(get(rota)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/estatisticas.html"));
        }
        String index = Files.readString(STATIC.resolve("index.html"));
        assertTrue(index.contains("/estatisticas.html") && index.contains("Auth.isGestor()"));
    }

    // ------------------------------------------------------ Criar anuncio

    @Test
    @DisplayName("CT812 - O botao/link 'Criar anuncio' existe so no Painel do gestor (e a propria pagina do formulario)")
    void criarAnuncioSoNoPainel() throws IOException {
        Set<String> permitidos = Set.of("dashboard.html", "js/calendario.js", "js/anuncio-form.js", "anuncio-form.html");
        Pattern criacao = Pattern.compile("anuncio-form\\.html(?![?\\w])");
        List<String> fora = new ArrayList<>();
        for (Path f : arquivos(STATIC, ".html", ".js")) {
            String rel = relativo(f);
            String conteudo = Files.readString(f);
            boolean temLink = criacao.matcher(conteudo).find();
            boolean temTexto = conteudo.contains("Criar anúncio");
            if ((temLink || temTexto) && !permitidos.contains(rel)) {
                fora.add(rel);
            }
        }
        assertTrue(fora.isEmpty(), "'Criar anuncio' fora do Painel: " + fora);
        assertTrue(Files.readString(STATIC.resolve("dashboard.html")).contains("id=\"btnCriar\""));
        // o menu principal nunca oferece criar anuncio
        assertFalse(Files.readString(STATIC.resolve("js/navegacao.js")).toLowerCase(Locale.ROOT).contains("criar"));
    }

    // ------------------------------------------------------------- fuso

    @Test
    @DisplayName("CT820 - O relogio da plataforma segue America/Sao_Paulo, nao o fuso da JVM; nome IANA, nunca deslocamento fixo")
    void relogioEmBrasilia() {
        ClockConfig cfg = new ClockConfig();
        ZoneId zona = cfg.zonaDaPlataforma("America/Sao_Paulo");
        Clock relogio = cfg.clock(zona);
        assertEquals("America/Sao_Paulo", relogio.getZone().getId());
        assertEquals(zona, PlataformaTempo.zona());
        assertThrows(Exception.class, () -> cfg.zonaDaPlataforma("fuso-que-nao-existe"), "nome invalido derruba a subida");
    }

    @Test
    @DisplayName("CT821 - Datas de reserva e bloqueio (data pura) nao 'andam' um dia com o fuso da JVM nem do navegador")
    void datasPurasNaoMudamDeDia() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            for (String id : List.of("Pacific/Kiritimati", "America/Sao_Paulo", "Pacific/Pago_Pago", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                var b = new CalendarioDtos.BloqueioPedido(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), "OUTRO", null, false, false);
                String json = mapper.writeValueAsString(b);
                assertTrue(json.contains("\"2026-12-31\"") && json.contains("\"2027-01-01\""), id + " -> " + json);
                var lido = mapper.readValue(json, CalendarioDtos.BloqueioPedido.class);
                assertEquals(LocalDate.of(2026, 12, 31), lido.dataInicio(), id);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("CT822 - Duracoes sao absolutas: 24h e 2h somam tempo decorrido, inclusive em um dia de 23 horas")
    void duracoesAbsolutas() throws Exception {
        Method somar = Class.forName("br.com.unisenai.smartrent.service.Agora")
                .getDeclaredMethod("somar", LocalDateTime.class, Duration.class, Clock.class);
        somar.setAccessible(true);
        Clock brasilia = Clock.system(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime base = LocalDateTime.of(2026, 10, 3, 12, 0);
        assertEquals(LocalDateTime.of(2026, 10, 4, 12, 0), somar.invoke(null, base, Duration.ofHours(24), brasilia));
        assertEquals(LocalDateTime.of(2026, 10, 3, 14, 0), somar.invoke(null, base, Duration.ofHours(2), brasilia));

        // Se a regra do fuso voltar a ter horario de verao, 24h continuam sendo 24h (aqui: 23h de relogio local)
        Clock comVerao = Clock.system(ZoneId.of("America/New_York"));
        LocalDateTime antesDoSalto = LocalDateTime.of(2026, 3, 7, 12, 0); // 8/3 as 2h os relogios adiantam 1h
        assertEquals(LocalDateTime.of(2026, 3, 8, 13, 0), somar.invoke(null, antesDoSalto, Duration.ofHours(24), comVerao));
    }

    @Test
    @DisplayName("CT823 - Virada de dia em Brasilia (21:00-03:00 UTC): 23:59 e 00:00 caem em dias diferentes; 'hoje' nao depende do fuso da JVM")
    void viradaDeDia() {
        ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
        Clock antes = Clock.fixed(Instant.parse("2026-10-04T02:59:00Z"), brasilia); // 23:59 de 03/10
        Clock depois = Clock.fixed(Instant.parse("2026-10-04T03:00:00Z"), brasilia); // 00:00 de 04/10
        assertEquals(LocalDate.of(2026, 10, 3), LocalDate.now(antes));
        assertEquals(LocalDate.of(2026, 10, 4), LocalDate.now(depois));
        // mesmo instante, relogio em Toquio: outro dia, mostrando que so o relogio da plataforma vale
        assertEquals(LocalDate.of(2026, 10, 4), LocalDate.now(Clock.fixed(Instant.parse("2026-10-04T02:59:00Z"), ZoneId.of("Asia/Tokyo"))));
    }
}
