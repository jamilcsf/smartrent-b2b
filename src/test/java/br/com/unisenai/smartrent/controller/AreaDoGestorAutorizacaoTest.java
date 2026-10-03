package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Calendario, bloqueios, cancelamento pelo gestor e area do cliente: papel verificado na cadeia real de filtros. */
@WebMvcTest({CalendarioController.class, ReservaController.class, ReservaClienteController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AreaDoGestorAutorizacaoTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CalendarioService calendarioService;
    @MockBean private BloqueioService bloqueioService;
    @MockBean private ReservaService reservaService;
    @MockBean private CancelamentoService cancelamentoService;
    @MockBean private ReservaClienteService reservaClienteService;
    @MockBean private ReservaClienteMapper reservaClienteMapper;
    @MockBean private JwtService jwtService;
    @MockBean private UsuarioRepository usuarioRepository;

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
    @DisplayName("CT360 - Usuario comum recebe 403 no calendario, nos bloqueios e no cancelamento do gestor; visitante 401")
    void rotasDoGestor() throws Exception {
        String cliente = "Bearer token-cliente";
        mockMvc.perform(get("/api/gestor/calendario?imovelId=1&de=2026-10-01&ate=2026-12-31")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/gestor/calendario?imovelId=1&de=2026-10-01&ate=2026-12-31").header("Authorization", cliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gestor/imoveis/1/bloqueios").header("Authorization", cliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/gestor/imoveis/1/bloqueios/1").header("Authorization", cliente)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reservas/1/cancelar").header("Authorization", cliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CT361 - Gestor acessa o calendario; area do cliente exige login (401) e aceita qualquer autenticado")
    void rotasDoCliente() throws Exception {
        mockMvc.perform(get("/api/gestor/calendario?imovelId=1&de=2026-10-01&ate=2026-12-31").header("Authorization", "Bearer token-gestor"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cliente/reservas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cliente/reservas/1/pagar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cliente/reservas").header("Authorization", "Bearer token-cliente")).andExpect(status().isOk());
    }
}
