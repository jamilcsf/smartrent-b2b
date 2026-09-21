package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.CadastroRequest;
import br.com.unisenai.smartrent.dto.LoginRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void preparar() {
        // Encoder real: o teste precisa provar que a senha vira hash de fato.
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
    }

    private Usuario usuarioComSenha(String senha) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Ana Beatriz Rocha");
        u.setEmail("ana@smartrent.dev");
        u.setSenhaHash(passwordEncoder.encode(senha));
        u.setPapel(PapelUsuario.ANFITRIAO);
        u.setAtivo(true);
        return u;
    }

    @Test
    @DisplayName("CT15 - Cadastro deve gravar a senha como hash, nunca em texto puro")
    void deveGravarSenhaComoHash() {
        when(usuarioRepository.findByEmail("ana@smartrent.dev")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.gerarToken(any())).thenReturn("token-ficticio");

        authService.cadastrar(new CadastroRequest(
                "Ana Beatriz Rocha", "ana@smartrent.dev", "senhaSegura123", "senhaSegura123"));

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        Usuario salvo = capturado.getValue();

        assertNotEquals("senhaSegura123", salvo.getSenhaHash());
        assertTrue(passwordEncoder.matches("senhaSegura123", salvo.getSenhaHash()));
        assertEquals(PapelUsuario.ANFITRIAO, salvo.getPapel());
        assertTrue(salvo.isAtivo());
    }

    @Test
    @DisplayName("CT16 - Cadastro deve normalizar o e-mail para minúsculas")
    void deveNormalizarEmail() {
        when(usuarioRepository.findByEmail("ana@smartrent.dev")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.gerarToken(any())).thenReturn("token-ficticio");

        authService.cadastrar(new CadastroRequest(
                "Ana", "  Ana@SmartRent.DEV  ", "senhaSegura123", "senhaSegura123"));

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertEquals("ana@smartrent.dev", capturado.getValue().getEmail());
    }

    @Test
    @DisplayName("CT17 - Cadastro deve recusar senha e confirmação diferentes")
    void deveRecusarConfirmacaoDivergente() {
        var req = new CadastroRequest("Ana", "ana@smartrent.dev", "senhaSegura123", "outraCoisa456");

        var erro = assertThrows(IllegalArgumentException.class, () -> authService.cadastrar(req));

        assertEquals("As senhas não conferem.", erro.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT18 - Cadastro deve recusar e-mail já existente")
    void deveRecusarEmailDuplicado() {
        when(usuarioRepository.findByEmail("ana@smartrent.dev"))
                .thenReturn(Optional.of(usuarioComSenha("qualquer")));

        var req = new CadastroRequest("Ana", "ana@smartrent.dev", "senhaSegura123", "senhaSegura123");

        assertThrows(AuthService.EmailJaCadastradoException.class, () -> authService.cadastrar(req));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT19 - Login deve emitir token quando a senha confere")
    void deveAutenticarComSenhaCorreta() {
        when(usuarioRepository.findByEmail("ana@smartrent.dev"))
                .thenReturn(Optional.of(usuarioComSenha("senhaSegura123")));
        when(jwtService.gerarToken(any())).thenReturn("token-valido");
        when(jwtService.getValidadeSegundos()).thenReturn(86400L);

        AuthResponse r = authService.autenticar(new LoginRequest("ana@smartrent.dev", "senhaSegura123"));

        assertEquals("token-valido", r.token());
        assertEquals("ana@smartrent.dev", r.usuario().email());
    }

    @Test
    @DisplayName("CT20 - Login deve recusar senha errada sem revelar qual campo falhou")
    void deveRecusarSenhaErrada() {
        when(usuarioRepository.findByEmail("ana@smartrent.dev"))
                .thenReturn(Optional.of(usuarioComSenha("senhaSegura123")));

        var erro = assertThrows(AuthService.CredenciaisInvalidasException.class,
                () -> authService.autenticar(new LoginRequest("ana@smartrent.dev", "errada")));

        assertEquals("E-mail ou senha inválidos.", erro.getMessage());
    }

    @Test
    @DisplayName("CT21 - Login de e-mail inexistente deve dar a mesma mensagem da senha errada")
    void deveRecusarEmailInexistenteComMesmaMensagem() {
        when(usuarioRepository.findByEmail("ninguem@smartrent.dev")).thenReturn(Optional.empty());

        var erro = assertThrows(AuthService.CredenciaisInvalidasException.class,
                () -> authService.autenticar(new LoginRequest("ninguem@smartrent.dev", "qualquer")));

        // Mensagem idêntica à da senha errada: distinguir permitiria descobrir
        // quais e-mails estão cadastrados.
        assertEquals("E-mail ou senha inválidos.", erro.getMessage());
    }

    @Test
    @DisplayName("CT22 - Login deve recusar usuário inativo")
    void deveRecusarUsuarioInativo() {
        Usuario inativo = usuarioComSenha("senhaSegura123");
        inativo.setAtivo(false);
        when(usuarioRepository.findByEmail("ana@smartrent.dev")).thenReturn(Optional.of(inativo));

        assertThrows(AuthService.CredenciaisInvalidasException.class,
                () -> authService.autenticar(new LoginRequest("ana@smartrent.dev", "senhaSegura123")));
    }
}
