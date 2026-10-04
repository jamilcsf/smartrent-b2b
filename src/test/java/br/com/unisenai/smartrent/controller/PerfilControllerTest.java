package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.FotoPerfilService;
import br.com.unisenai.smartrent.service.PerfilService;
import br.com.unisenai.smartrent.service.SenhaService;
import br.com.unisenai.smartrent.service.TrocaEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Perfil: exige login, vale so para o proprio usuario e respeita a versao de sessao do token. */
@WebMvcTest({PerfilController.class, FotoPerfilController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PerfilControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PerfilService perfilService;
    @MockBean private FotoPerfilService fotoPerfilService;
    @MockBean private SenhaService senhaService;
    @MockBean private TrocaEmailService trocaEmailService;
    @MockBean private JwtService jwtService;
    @MockBean private UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNome("Ana Rocha");
        usuario.setEmail("ana@smartrent.dev");
        usuario.setPapel(PapelUsuario.CLIENTE);
        usuario.setAtivo(true);
        when(jwtService.emailDoTokenOuNull("tk")).thenReturn(usuario.getEmail());
        when(jwtService.versaoDaSessao("tk")).thenReturn(0);
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(perfilService.obter(any())).thenReturn(new PerfilResponse("Ana Rocha", "ana@smartrent.dev",
                PapelUsuario.CLIENTE, null, LocalDateTime.of(2026, 1, 2, 3, 4), true, null, List.of(), null));
    }

    @Test
    @DisplayName("CT438 - Perfil exige login (401) e devolve so dados do proprio usuario, sem id")
    void perfilExigeLogin() throws Exception {
        mockMvc.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer tk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@smartrent.dev"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("CT439 - Token emitido antes de uma troca de senha/e-mail (versao antiga) e tratado como anonimo")
    void tokenDeSessaoAntigaNaoVale() throws Exception {
        usuario.setSessaoVersao(1); // senha trocada depois da emissao do token "tk" (versao 0)
        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer tk")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT440 - Nenhum endpoint do perfil recebe id de usuario (anti-IDOR por construcao)")
    void semIdDeUsuarioNaEntrada() {
        for (Method m : PerfilController.class.getDeclaredMethods()) {
            for (var p : m.getParameters()) {
                assertNull(p.getAnnotation(PathVariable.class), m.getName() + " nao pode receber id na URL");
                RequestParam rp = p.getAnnotation(RequestParam.class);
                if (rp != null) {
                    assertFalse(p.getName().toLowerCase().contains("usuario") || p.getName().equalsIgnoreCase("id"),
                            m.getName() + " nao pode receber id de usuario");
                }
                if (p.getType().isRecord()) {
                    for (var c : p.getType().getRecordComponents()) {
                        String n = c.getName().toLowerCase();
                        assertFalse(n.equals("id") || n.equals("usuarioid"), p.getType().getSimpleName() + " nao pode ter id de usuario");
                    }
                }
            }
        }
    }
}
