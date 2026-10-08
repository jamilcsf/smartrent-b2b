package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AdminDtos.ImovelAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.Pagina;
import br.com.unisenai.smartrent.dto.AdminDtos.ReservaAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.UsuarioAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.VisaoGeral;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.MapaDeCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ResumoUso;
import br.com.unisenai.smartrent.service.AdminService;
import br.com.unisenai.smartrent.service.TelemetriaAnaliseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * Painel de admin: tudo sob {@code /api/admin/**}, que o SecurityConfig restringe ao papel ADMIN (gestor e cliente
 * recebem 403, visitante 401). Mapa de calor e demais metricas de uso vem da telemetria anonima (ADR-009).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final TelemetriaAnaliseService telemetria;

    public AdminController(AdminService admin, TelemetriaAnaliseService telemetria) {
        this.admin = admin;
        this.telemetria = telemetria;
    }

    @GetMapping("/visao-geral")
    public VisaoGeral visaoGeral(@RequestParam(required = false) Integer dias) {
        return admin.visaoGeral(dias);
    }

    @GetMapping("/usuarios")
    public Pagina<UsuarioAdmin> usuarios(@RequestParam(required = false) String busca,
                                         @RequestParam(required = false) String papel,
                                         @RequestParam(required = false) Integer pagina,
                                         @RequestParam(required = false) Integer tamanho) {
        return admin.usuarios(busca, papel, pagina, tamanho);
    }

    @GetMapping("/imoveis")
    public Pagina<ImovelAdmin> imoveis(@RequestParam(required = false) String busca,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer pagina,
                                       @RequestParam(required = false) Integer tamanho) {
        return admin.imoveis(busca, status, pagina, tamanho);
    }

    @GetMapping("/reservas")
    public Pagina<ReservaAdmin> reservas(@RequestParam(required = false) String busca,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer pagina,
                                         @RequestParam(required = false) Integer tamanho) {
        return admin.reservas(busca, status, pagina, tamanho);
    }

    // ---- Uso do site (telemetria) ----

    @GetMapping("/uso/resumo")
    public ResumoUso resumoUso(@RequestParam(required = false) Integer dias) {
        return telemetria.resumo(dias);
    }

    @GetMapping("/uso/paginas")
    public List<String> paginas(@RequestParam(required = false) Integer dias) {
        return telemetria.paginas(dias);
    }

    @GetMapping("/uso/mapa-de-calor")
    public MapaDeCalor mapaDeCalor(@RequestParam String pagina,
                                   @RequestParam(required = false) String dispositivo,
                                   @RequestParam(required = false) Integer dias) {
        return telemetria.mapa(pagina, dispositivo, dias);
    }

    /** Eventos brutos para analise externa (big data): CSV ou NDJSON, em fluxo. */
    @GetMapping("/uso/exportar")
    public ResponseEntity<StreamingResponseBody> exportar(@RequestParam(defaultValue = "csv") String formato,
                                                          @RequestParam(required = false) Integer dias) {
        boolean csv = !"ndjson".equalsIgnoreCase(formato);
        StreamingResponseBody corpo = saida -> telemetria.exportar(saida, dias, csv);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eventos-uso." + (csv ? "csv" : "ndjson") + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(csv ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                        : MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .body(corpo);
    }
}
