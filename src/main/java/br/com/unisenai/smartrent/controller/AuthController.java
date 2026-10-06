package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AuthConfigResponse;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.CadastroRequest;
import br.com.unisenai.smartrent.dto.GoogleLoginRequest;
import br.com.unisenai.smartrent.dto.LoginRequest;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.AccountRestrictionService;
import br.com.unisenai.smartrent.service.AuditoriaContaService;
import br.com.unisenai.smartrent.service.AuthService;
import br.com.unisenai.smartrent.service.CaptchaService;
import br.com.unisenai.smartrent.service.GoogleTokenVerifier;
import br.com.unisenai.smartrent.service.LimitesDeAutenticacao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuditoriaContaService auditoriaConta;
    private final AccountRestrictionService restricoes;
    private final LimitesDeAutenticacao limites;

    public AuthController(AuthService authService,
                          CaptchaService captchaService,
                          GoogleTokenVerifier googleTokenVerifier,
                          AuditoriaContaService auditoriaConta,
                          AccountRestrictionService restricoes,
                          LimitesDeAutenticacao limites) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.auditoriaConta = auditoriaConta;
        this.restricoes = restricoes;
        this.limites = limites;
    }

    /** Chaves públicas para o front montar o captcha e o botão do Google. */
    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> config() {
        return ResponseEntity.ok(new AuthConfigResponse(
                captchaService.getSiteKey(), googleTokenVerifier.getClientId()));
    }

    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponse> cadastrar(@Valid @RequestBody CadastroRequest req, HttpServletRequest http) {
        limites.exigirCadastro(http.getRemoteAddr());
        // Sem captcha, qualquer script criaria contas em massa.
        captchaService.verificar(req.captchaToken());
        return ResponseEntity.ok(authService.cadastrar(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // O captcha vem antes de tocar na senha: sem ele, a API seria um
        // oráculo de tentativa e erro para robôs.
        String ip = http.getRemoteAddr();
        limites.exigirLogin(ip, req.email());
        captchaService.verificar(req.captchaToken());
        AuthResponse resposta;
        try {
            resposta = authService.autenticar(req);
        } catch (AuthService.CredenciaisInvalidasException e) {
            limites.registrarFalhaDeLogin(ip, req.email());
            throw e;
        }
        limites.registrarLoginValido(req.email());
        registrarLogin(resposta, http);
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginGoogle(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest http) {
        var identidade = googleTokenVerifier.verificar(req.credential());
        AuthResponse resposta = authService.entrarComGoogle(identidade.email(), identidade.nome(), req.perfil());
        registrarLogin(resposta, http);
        return ResponseEntity.ok(resposta);
    }

    /** O IP de cada login alimenta o sinal "IP novo" da analise de pedidos de exclusao de dados. */
    private void registrarLogin(AuthResponse resposta, HttpServletRequest http) {
        if (resposta != null && resposta.usuario() != null && resposta.usuario().id() != null) {
            auditoriaConta.registrar(resposta.usuario().id(), AuditoriaContaService.LOGIN, null, http.getRemoteAddr());
        }
    }

    /**
     * Unica rota que exige identificacao. Serve ao front para revalidar a
     * sessao no carregamento e, sobretudo, como gatilho real de 401 para o
     * tratamento global de erros.
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> eu(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(UsuarioResponse.de(usuario, restricoes.restricoesAtivas(usuario)));
    }
}
