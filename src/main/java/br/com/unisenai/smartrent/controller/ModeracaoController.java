package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AdminDtos.Pagina;
import br.com.unisenai.smartrent.dto.AdminDtos.UsuarioAdmin;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.AcaoItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.AlertaItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.ComunicadoEnviado;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.ContaSuspensa;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisaoDenunciaRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisoesAutomaticas;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DenunciaDetalhe;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DenunciaItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MensagemAdminRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MotivoRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.NivelRemetenteItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.RevisaoAlertaRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ModeracaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Moderacao no painel de admin (ADR-010). Tudo sob {@code /api/admin/**}: so ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/moderacao")
public class ModeracaoController {

    private final ModeracaoService service;

    public ModeracaoController(ModeracaoService service) {
        this.service = service;
    }

    // ---- Denuncias ----

    @GetMapping("/denuncias")
    public Pagina<DenunciaItem> denuncias(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Integer pagina,
                                          @RequestParam(required = false) Integer tamanho) {
        return service.denuncias(status, pagina, tamanho);
    }

    @GetMapping("/denuncias/{id}")
    public DenunciaDetalhe denuncia(@AuthenticationPrincipal Usuario admin, @PathVariable Long id) {
        return service.denuncia(admin, id);
    }

    @PostMapping("/denuncias/{id}/decisao")
    public DenunciaItem decidir(@AuthenticationPrincipal Usuario admin, @PathVariable Long id,
                                @RequestBody DecisaoDenunciaRequest req, HttpServletRequest http) {
        return service.decidirDenuncia(admin, id, req, http.getRemoteAddr());
    }

    // ---- Decisoes automaticas ----

    @GetMapping("/automatizadas")
    public DecisoesAutomaticas automatizadas(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) Integer dias,
                                             @RequestParam(required = false) Integer pagina,
                                             @RequestParam(required = false) Integer tamanho) {
        return service.decisoesAutomaticas(status, dias, pagina, tamanho);
    }

    @PostMapping("/alertas/{id}/revisao")
    public AlertaItem revisar(@AuthenticationPrincipal Usuario admin, @PathVariable Long id,
                              @RequestBody RevisaoAlertaRequest req) {
        return service.revisarAlerta(admin, id, req);
    }

    // ---- Contas ----

    @GetMapping("/contas-suspensas")
    public Pagina<ContaSuspensa> contasSuspensas(@RequestParam(required = false) Integer pagina,
                                                 @RequestParam(required = false) Integer tamanho) {
        return service.contasSuspensas(pagina, tamanho);
    }

    @PostMapping("/usuarios/{id}/suspensao")
    public UsuarioAdmin suspender(@AuthenticationPrincipal Usuario admin, @PathVariable Long id,
                                  @RequestBody MotivoRequest req, HttpServletRequest http) {
        return service.suspender(admin, id, req == null ? null : req.motivo(), http.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/reativacao")
    public UsuarioAdmin reativar(@AuthenticationPrincipal Usuario admin, @PathVariable Long id,
                                 @RequestBody MotivoRequest req, HttpServletRequest http) {
        return service.reativar(admin, id, req == null ? null : req.motivo(), http.getRemoteAddr());
    }

    @GetMapping("/acoes")
    public Pagina<AcaoItem> acoes(@RequestParam(required = false) Long usuarioId,
                                  @RequestParam(required = false) Integer pagina,
                                  @RequestParam(required = false) Integer tamanho) {
        return service.historico(usuarioId, pagina, tamanho);
    }

    // ---- Avisos ----

    @GetMapping("/niveis")
    public List<NivelRemetenteItem> niveis() {
        return service.niveis();
    }

    @PostMapping("/usuarios/{id}/mensagens")
    public ComunicadoEnviado mensagem(@AuthenticationPrincipal Usuario admin, @PathVariable Long id,
                                      @RequestBody MensagemAdminRequest req) {
        return service.enviarMensagem(admin, id, req);
    }

    @GetMapping("/comunicados")
    public Pagina<ComunicadoEnviado> comunicados(@RequestParam(required = false) Integer pagina,
                                                 @RequestParam(required = false) Integer tamanho) {
        return service.comunicadosEnviados(pagina, tamanho);
    }
}
