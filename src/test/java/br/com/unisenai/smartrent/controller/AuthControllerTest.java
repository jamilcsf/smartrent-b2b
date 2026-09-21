package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.AuthService;
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
                new UsuarioResponse(1L, "Ana", "ana@smartrent.dev", PapelUsuario.ANFITRIAO)));

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
}
