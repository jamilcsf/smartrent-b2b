package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AuthConfigResponse;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.CadastroRequest;
import br.com.unisenai.smartrent.dto.GoogleLoginRequest;
import br.com.unisenai.smartrent.dto.LoginRequest;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.AuthService;
import br.com.unisenai.smartrent.service.CaptchaService;
import br.com.unisenai.smartrent.service.GoogleTokenVerifier;
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

    public AuthController(AuthService authService,
                          CaptchaService captchaService,
                          GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    /** Chaves públicas para o front montar o captcha e o botão do Google. */
    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> config() {
        return ResponseEntity.ok(new AuthConfigResponse(
                captchaService.getSiteKey(), googleTokenVerifier.getClientId()));
    }

    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponse> cadastrar(@Valid @RequestBody CadastroRequest req) {
        return ResponseEntity.ok(authService.cadastrar(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        // O captcha vem antes de tocar na senha: sem ele, a API seria um
        // oráculo de tentativa e erro para robôs.
        captchaService.verificar(req.captchaToken());
        return ResponseEntity.ok(authService.autenticar(req));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginGoogle(@Valid @RequestBody GoogleLoginRequest req) {
        var identidade = googleTokenVerifier.verificar(req.credential());
        return ResponseEntity.ok(authService.entrarComGoogle(identidade.email(), identidade.nome()));
    }

    /**
     * Unica rota que exige identificacao. Serve ao front para revalidar a
     * sessao no carregamento e, sobretudo, como gatilho real de 401 para o
     * tratamento global de erros.
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> eu(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(UsuarioResponse.de(usuario));
    }
}
