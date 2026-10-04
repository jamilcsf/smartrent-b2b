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

import java.util.UUID;

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
        usuario.setPapel(papelDoPerfil(req.perfil()));
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

    /**
     * Entra com uma identidade já validada pelo Google. O e-mail verificado
     * pelo Google identifica a conta: se existir, entra nela; se não, cria uma.
     */
    @Transactional
    public AuthResponse entrarComGoogle(String emailGoogle, String nomeGoogle) {
        return entrarComGoogle(emailGoogle, nomeGoogle, null);
    }

    @Transactional
    public AuthResponse entrarComGoogle(String emailGoogle, String nomeGoogle, String perfil) {
        String email = emailGoogle.trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseGet(() -> usuarioRepository.save(novoUsuarioGoogle(email, nomeGoogle, perfil)));

        if (!usuario.isAtivo()) {
            throw new CredenciaisInvalidasException("Esta conta está inativa.");
        }
        return responder(usuario);
    }

    private Usuario novoUsuarioGoogle(String email, String nomeGoogle, String perfil) {
        String nome = (nomeGoogle == null || nomeGoogle.isBlank())
                ? email.substring(0, email.indexOf('@'))
                : nomeGoogle.trim();

        Usuario usuario = new Usuario();
        usuario.setNome(nome.length() > 120 ? nome.substring(0, 120) : nome);
        usuario.setEmail(email);
        // A coluna exige hash. Conta criada pelo Google recebe o hash de uma
        // senha aleatória que ninguém conhece: só entra pelo Google.
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setSenhaDefinida(false);
        usuario.setPapel(papelDoPerfil(perfil));
        usuario.setAtivo(true);
        return usuario;
    }

    /**
     * O cadastro publico nunca cria ADMIN. Perfil ausente ou desconhecido vira
     * CLIENTE: errar para o lado do menor privilegio.
     */
    private static PapelUsuario papelDoPerfil(String perfil) {
        return "GESTOR".equalsIgnoreCase(perfil == null ? "" : perfil.trim())
                ? PapelUsuario.ANFITRIAO
                : PapelUsuario.CLIENTE;
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
