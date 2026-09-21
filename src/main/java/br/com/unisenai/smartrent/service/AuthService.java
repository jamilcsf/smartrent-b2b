package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.CadastroRequest;
import br.com.unisenai.smartrent.dto.LoginRequest;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse cadastrar(CadastroRequest req) {
        if (!req.senha().equals(req.confirmacaoSenha())) {
            throw new IllegalArgumentException("As senhas não conferem.");
        }
        String email = req.email().trim().toLowerCase();
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new EmailJaCadastradoException("Já existe uma conta com este e-mail.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(req.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuario.setPapel(PapelUsuario.ANFITRIAO);
        usuario.setAtivo(true);

        return responder(usuarioRepository.save(usuario));
    }

    public AuthResponse autenticar(LoginRequest req) {
        String email = req.email().trim().toLowerCase();

        // Mensagem unica para e-mail inexistente e senha errada: dizer qual dos
        // dois falhou permitiria descobrir quais e-mails estao cadastrados.
        Usuario usuario = usuarioRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos."));

        return responder(usuario);
    }

    private AuthResponse responder(Usuario usuario) {
        return new AuthResponse(jwtService.gerarToken(usuario),
                jwtService.getValidadeSegundos(),
                UsuarioResponse.de(usuario));
    }

    public static class EmailJaCadastradoException extends RuntimeException {
        public EmailJaCadastradoException(String m) {
            super(m);
        }
    }

    public static class CredenciaisInvalidasException extends RuntimeException {
        public CredenciaisInvalidasException(String m) {
            super(m);
        }
    }
}
