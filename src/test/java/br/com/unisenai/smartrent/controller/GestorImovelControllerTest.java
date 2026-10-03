package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.AnuncioEdicaoService;
import br.com.unisenai.smartrent.service.AnuncioService;
import br.com.unisenai.smartrent.service.MidiaService;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Autorizacao por papel nos endpoints do gestor, atraves da cadeia real de
 * filtros: um usuario comum que chamar a API diretamente recebe 403.
 */
@WebMvcTest(GestorImovelController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class GestorImovelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnuncioService anuncioService;
    @MockBean
    private AnuncioEdicaoService anuncioEdicaoService;
    @MockBean
    private MidiaService midiaService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UsuarioRepository usuarioRepository;

    private static final String JSON_ANUNCIO = """
            {"dados":{"titulo":"Apto","tipoImovel":"APARTAMENTO","metragemQuadrada":50,"numeroQuartos":1,
            "numeroBanheiros":1,"capacidadeHospedes":2,"cep":"88054-000","logradouro":"Rua A","numero":"1",
            "bairro":"Centro","cidade":"Florianopolis","estado":"SC"},"aceiteTermo":true}
            """;

    @BeforeEach
    void usuarios() {
        login("token-cliente", 1L, PapelUsuario.CLIENTE);
        login("token-gestor", 2L, PapelUsuario.ANFITRIAO);
        login("token-admin", 3L, PapelUsuario.ADMIN);
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
    @DisplayName("CT100 - Visitante sem token recebe 401 nos endpoints do gestor")
    void visitanteRecebe401() throws Exception {
        mockMvc.perform(get("/api/gestor/imoveis")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/gestor/imoveis").contentType(MediaType.APPLICATION_JSON).content(JSON_ANUNCIO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT101 - Usuario comum (CLIENTE) chamando a API diretamente recebe 403 em todas as rotas do gestor")
    void clienteRecebe403() throws Exception {
        String auth = "Bearer token-cliente";
        mockMvc.perform(get("/api/gestor/imoveis").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.erro").exists());
        mockMvc.perform(post("/api/gestor/imoveis").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_ANUNCIO))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/gestor/imoveis/1/preco").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":300}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gestor/imoveis/1/publicar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceiteTermo\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/gestor/imoveis/1/midias")
                        .file(new MockMultipartFile("arquivo", "a.jpg", "image/jpeg", new byte[]{1}))
                        .param("tipo", "FOTO").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gestor/imoveis/1/edicao/iniciar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmado\":true}"))
                .andExpect(status().isForbidden());

        verify(anuncioService, never()).criar(any(), any(), any());
        verify(anuncioService, never()).definirPreco(any(), any(), any(), any());
        verify(midiaService, never()).adicionar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CT102 - Gestor lista os anuncios; admin tambem entra")
    void gestorEAdminPassam() throws Exception {
        when(anuncioService.listarMeus(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/gestor/imoveis").header("Authorization", "Bearer token-gestor"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gestor/imoveis").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT103 - Cadastro com dados invalidos devolve 400 com o erro de cada campo")
    void cadastroInvalido() throws Exception {
        mockMvc.perform(post("/api/gestor/imoveis").header("Authorization", "Bearer token-gestor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dados\":{\"titulo\":\"\",\"metragemQuadrada\":-5,\"whatsappLink\":\"http://golpe.com\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").exists());
        verify(anuncioService, never()).criar(any(), any(), any());
    }

    @Test
    @DisplayName("CT104 - Gestor acessando imovel de outro recebe 403 vindo do servico")
    void imovelAlheioRecebe403() throws Exception {
        when(anuncioService.obter(any(), any())).thenThrow(new AcessoNegadoException("Você não tem permissão sobre este imóvel."));
        mockMvc.perform(get("/api/gestor/imoveis/77").header("Authorization", "Bearer token-gestor"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").exists());
    }
}
