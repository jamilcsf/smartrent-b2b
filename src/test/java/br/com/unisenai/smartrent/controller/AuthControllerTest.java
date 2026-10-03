package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.AuthService;
import br.com.unisenai.smartrent.service.CaptchaService;
import br.com.unisenai.smartrent.service.GoogleTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrato HTTP da autenticacao, exercitado atraves da cadeia de filtros real
 * do Spring Security — e o que permite comprovar o soft gating e o 401.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("CT27 - /api/auth/me sem token deve responder 401 em JSON")
    void deveResponder401SemToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("CT28 - Soft gating: caminho público inexistente responde 404, não 401")
    void naoDeveBloquearCaminhoPublico() throws Exception {
        // Se a cadeia estivesse fechada por padrão, o status seria 401.
        mockMvc.perform(get("/api/caminho-que-nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CT29 - Login é acessível a visitante e devolve o token")
    void loginDeveSerPublico() throws Exception {
        when(authService.autenticar(any())).thenReturn(new AuthResponse("token-abc", 86400L,
                new UsuarioResponse(1L, "Ana", "ana@smartrent.dev", PapelUsuario.ANFITRIAO, false)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ana@smartrent.dev", "senha", "senhaSegura123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.usuario.email").value("ana@smartrent.dev"));
    }

    @Test
    @DisplayName("CT30 - Cadastro inválido deve responder 400 com o erro de cada campo")
    void cadastroInvalidoDeveDetalharCampos() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "",
                                "email", "nao-e-email",
                                "senha", "123",
                                "confirmacaoSenha", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    @DisplayName("CT31 - E-mail já cadastrado deve responder 409")
    void emailDuplicadoDeveResponder409() throws Exception {
        when(authService.cadastrar(any()))
                .thenThrow(new AuthService.EmailJaCadastradoException("Já existe uma conta com este e-mail."));

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Ana",
                                "email", "ana@smartrent.dev",
                                "senha", "senhaSegura123",
                                "confirmacaoSenha", "senhaSegura123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("CT13 - Recusar autenticação com credenciais inválidas")
    void credenciaisInvalidasDevemResponder401() throws Exception {
        when(authService.autenticar(any()))
                .thenThrow(new AuthService.CredenciaisInvalidasException("E-mail ou senha inválidos."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ana@smartrent.dev", "senha", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    @DisplayName("CT32 - Login sem captcha válido deve responder 400 sem consultar a senha")
    void loginSemCaptchaDeveSerRecusado() throws Exception {
        doThrow(new CaptchaService.CaptchaInvalidoException("Confirme que você não é um robô."))
                .when(captchaService).verificar(any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ana@smartrent.dev", "senha", "senhaSegura123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Confirme que você não é um robô."));

        verify(authService, never()).autenticar(any());
    }

    @Test
    @DisplayName("CT33 - Login com Google válido deve devolver o token")
    void loginGoogleDeveDevolverToken() throws Exception {
        when(googleTokenVerifier.verificar("credencial-google"))
                .thenReturn(new GoogleTokenVerifier.IdentidadeGoogle("ana@smartrent.dev", "Ana"));
        when(authService.entrarComGoogle("ana@smartrent.dev", "Ana", null)).thenReturn(new AuthResponse("token-g", 86400L,
                new UsuarioResponse(1L, "Ana", "ana@smartrent.dev", PapelUsuario.ANFITRIAO, false)));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", "credencial-google"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-g"));
    }

    @Test
    @DisplayName("CT34 - Credencial do Google inválida deve responder 401")
    void credencialGoogleInvalidaDeveResponder401() throws Exception {
        when(googleTokenVerifier.verificar(any()))
                .thenThrow(new GoogleTokenVerifier.TokenGoogleInvalidoException("Credencial do Google inválida ou expirada."));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", "lixo"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("CT35 - /config expõe só as chaves públicas, a visitantes")
    void configDeveSerPublica() throws Exception {
        when(captchaService.getSiteKey()).thenReturn("site-key");
        when(googleTokenVerifier.getClientId()).thenReturn("client-id");

        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recaptchaSiteKey").value("site-key"))
                .andExpect(jsonPath("$.googleClientId").value("client-id"));
    }

    @Test
    @DisplayName("CT48 - Cadastro sem captcha válido deve responder 400 sem criar a conta")
    void cadastroSemCaptchaDeveSerRecusado() throws Exception {
        doThrow(new CaptchaService.CaptchaInvalidoException("Confirme que você não é um robô."))
                .when(captchaService).verificar(any());

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Ana",
                                "email", "ana@smartrent.dev",
                                "senha", "senhaSegura123",
                                "confirmacaoSenha", "senhaSegura123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Confirme que você não é um robô."));

        verify(authService, never()).cadastrar(any());
    }
}
