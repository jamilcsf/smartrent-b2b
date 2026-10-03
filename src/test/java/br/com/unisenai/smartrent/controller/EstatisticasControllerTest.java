package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.EstatisticasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O Dashboard e do gestor: usuario comum e visitante nao chegam ao servico. */
@WebMvcTest(EstatisticasController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EstatisticasControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private EstatisticasService service;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void usuarios() {
        login("token-cliente", 1L, PapelUsuario.CLIENTE);
        login("token-gestor", 2L, PapelUsuario.ANFITRIAO);
    }

    private void login(String token, Long id, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail(token + "@smartrent.dev");
        u.setPapel(papel);
        u.setAtivo(true);
        when(jwtService.emailDoTokenOuNull(token)).thenReturn(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
    }

    @Test
    @DisplayName("CT214 - Dashboard: visitante 401, cliente 403 e gestor 200")
    void autorizacao() throws Exception {
        mockMvc.perform(get("/api/gestor/estatisticas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gestor/estatisticas").header("Authorization", "Bearer token-cliente"))
                .andExpect(status().isForbidden());
        verify(service, never()).calcular(any(), any());

        mockMvc.perform(get("/api/gestor/estatisticas?meses=3").header("Authorization", "Bearer token-gestor"))
                .andExpect(status().isOk());
        verify(service).calcular(any(Usuario.class), org.mockito.ArgumentMatchers.eq(3));
    }
}
