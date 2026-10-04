package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Catalogo publico: so anuncios visiveis e WhatsApp apenas para quem esta logado. */
@WebMvcTest(ImovelController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ImovelControllerTest.RelogioConfig.class})
class ImovelControllerTest {

    @TestConfiguration
    static class RelogioConfig {
        @Bean
        Clock clock() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 1, 9, 0));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ImovelRepository imovelRepository;
    @MockBean
    private ImovelMidiaRepository midiaRepository;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void preparar() {
        Imovel imovel = new Imovel();
        imovel.setId(1L);
        imovel.setTitulo("Apto Canasvieiras");
        imovel.setTipoImovel(TipoImovel.APARTAMENTO);
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        imovel.setAtivo(true);
        imovel.setMinimoDiarias(2);
        imovel.setTaxaLimpeza(new java.math.BigDecimal("80.00"));
        when(imovelRepository.findVisiveis(any())).thenReturn(List.of(imovel));
        when(imovelRepository.findVisivelPorId(any(), any())).thenReturn(Optional.of(imovel));
        when(midiaRepository.findByImovelIdInOrderByOrdemAscIdAsc(any())).thenReturn(List.of());
        when(midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(any())).thenReturn(List.of());

        Usuario cliente = new Usuario();
        cliente.setId(5L);
        cliente.setEmail("cliente@smartrent.dev");
        cliente.setPapel(PapelUsuario.CLIENTE);
        cliente.setAtivo(true);
        when(jwtService.emailDoTokenOuNull("token-cliente")).thenReturn(cliente.getEmail());
        when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(Optional.of(cliente));
    }

    @Test
    @DisplayName("CT105 - Nenhuma resposta publica traz campo de WhatsApp (anonimo ou logado); traz minimo de diarias e taxa de limpeza")
    void respostaNaoTemWhatsapp() throws Exception {
        mockMvc.perform(get("/api/imoveis/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Apto Canasvieiras"))
                .andExpect(jsonPath("$.minimoDiarias").value(2))
                .andExpect(jsonPath("$.taxaLimpeza").value(80.00))
                .andExpect(jsonPath("$.whatsappLink").doesNotExist());
        mockMvc.perform(get("/api/imoveis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].whatsappLink").doesNotExist());
        mockMvc.perform(get("/api/imoveis/1").header("Authorization", "Bearer token-cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappLink").doesNotExist());
    }

    @Test
    @DisplayName("CT107 - Imovel fora do catalogo (em edicao, pre-publicacao...) responde 404 na API publica")
    void foraDoCatalogoE404() throws Exception {
        when(imovelRepository.findVisivelPorId(any(), any())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/imoveis/1")).andExpect(status().isNotFound());
    }
}
