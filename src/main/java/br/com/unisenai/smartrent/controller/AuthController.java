package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.CadastroRequest;
import br.com.unisenai.smartrent.dto.LoginRequest;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponse> cadastrar(@Valid @RequestBody CadastroRequest req) {
        return ResponseEntity.ok(authService.cadastrar(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.autenticar(req));
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
