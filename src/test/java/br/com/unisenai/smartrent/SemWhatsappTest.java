package br.com.unisenai.smartrent;

import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.ReservaResponse;
import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.dto.ImovelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O contato e exclusivamente pelo SmartChat: nao pode sobrar campo, botao, endpoint
 * ou texto funcional de WhatsApp. Esta e a busca final, repetida a cada build.
 */
class SemWhatsappTest {

    private static final Pattern PADRAO = Pattern.compile("whatsapp|wa\\.me|api\\.whatsapp", Pattern.CASE_INSENSITIVE);

    /** Unicos lugares onde a palavra pode aparecer: historico de migrations, o termo juridico (sinalizado, nao reescrito) e o filtro que a bloqueia. */
    private static boolean permitido(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/db/migration/") || s.endsWith("/service/TermoUso.java")
                // lista de mensageiros que o filtro do SmartChat MASCARA (e o oposto de oferecer o canal)
                || s.endsWith("/service/MessageFilterService.java");
    }

    @Test
    @DisplayName("CT800 - Nenhuma referencia a WhatsApp no codigo, nas paginas e nos scripts de producao (alem do termo sinalizado ao juridico)")
    void semReferenciasFuncionais() throws IOException {
        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(Path.of("src/main"))) {
            for (Path p : (Iterable<Path>) arquivos.filter(Files::isRegularFile)::iterator) {
                String nome = p.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean texto = nome.endsWith(".java") || nome.endsWith(".js") || nome.endsWith(".html") || nome.endsWith(".properties")
                        || nome.endsWith(".txt") || nome.endsWith(".sql") || nome.endsWith(".md");
                if (!texto || permitido(p)) {
                    continue;
                }
                String conteudo = Files.readString(p);
                if (PADRAO.matcher(conteudo).find()) {
                    achados.add(p.toString());
                }
            }
        }
        assertTrue(achados.isEmpty(), "Referencias a WhatsApp encontradas: " + achados);
    }

    @Test
    @DisplayName("CT801 - Nenhum DTO de anuncio, imovel ou reserva tem campo de WhatsApp ou telefone do hospede")
    void dtosSemCampo() {
        for (Class<?> dto : new Class<?>[]{AnuncioDados.class, ImovelResponse.class, ReservaResponse.class, ReservaRequest.class}) {
            for (var c : dto.getRecordComponents()) {
                String n = c.getName().toLowerCase(Locale.ROOT);
                assertFalse(n.contains("whatsapp") || n.contains("telefone"), dto.getSimpleName() + "." + c.getName());
            }
        }
    }

    @Test
    @DisplayName("CT802 - Rascunho antigo com o campo whatsappLink continua legivel (o campo e ignorado, sem quebrar a leitura)")
    void rascunhoAntigoNaoQuebra() throws IOException {
        String antigo = """
                {"titulo":"Apto","descricao":"d","tipoImovel":"APARTAMENTO","metragemQuadrada":50,"numeroQuartos":1,"numeroBanheiros":1,
                 "capacidadeHospedes":2,"comodidades":[],"cep":"88054-000","logradouro":"Rua A","numero":"1","bairro":"Centro",
                 "cidade":"Florianopolis","estado":"SC","whatsappLink":"https://wa.me/5548999990000","valorDiaria":300}
                """;
        AnuncioDados d = new ObjectMapper().readValue(antigo, AnuncioDados.class); // mapper estrito: sem tolerancia do Spring Boot
        assertEquals("Apto", d.titulo());
        assertNull(d.minimoDiarias(), "campos novos ausentes viram nulos (padrao aplicado ao gravar)");
        assertFalse(new ObjectMapper().writeValueAsString(d).toLowerCase(Locale.ROOT).contains("whatsapp"));
    }
}
