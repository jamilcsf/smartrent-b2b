package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Confirmacao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.ChatEventos;
import br.com.unisenai.smartrent.service.SmartChatService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bean Validation nos pedidos do chat e cabecalhos de seguranca da cadeia real de filtros. */
@WebMvcTest(SmartChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SmartChatValidacaoTest {

    private static final String CONVERSA = "/api/smartchat/conversas/" + UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockBean private SmartChatService service;
    @MockBean private ChatEventos eventos;
    @MockBean private JwtService jwtService;
    @MockBean private UsuarioRepository usuarioRepository;

    @BeforeEach
    void usuario() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setEmail("cli@smartrent.dev");
        u.setPapel(PapelUsuario.CLIENTE);
        u.setAtivo(true);
        when(jwtService.emailDoTokenOuNull("tk")).thenReturn(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
    }

    private org.springframework.test.web.servlet.ResultActions enviar(String rota, String json) throws Exception {
        return mockMvc.perform(post(CONVERSA + rota).header("Authorization", "Bearer tk")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("CT1080 - Mensagem vazia, em branco ou gigante: 400 com o campo, sem chegar ao servico")
    void mensagemInvalida() throws Exception {
        enviar("/mensagens", "{\"texto\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.texto").value("Escreva uma mensagem."));
        enviar("/mensagens", "{}").andExpect(status().isBadRequest());
        enviar("/mensagens", "{\"texto\":\"" + "a".repeat(4001) + "\"}").andExpect(status().isBadRequest());
        verify(service, never()).enviar(any(), any(), any());
    }

    @Test
    @DisplayName("CT1081 - Denuncia sem motivo ou com lista enorme de mensagens: 400")
    void denunciaInvalida() throws Exception {
        enviar("/denuncias", "{\"motivo\":\"\"}").andExpect(status().isBadRequest());
        String ids = ("\"" + UUID.randomUUID() + "\",").repeat(51);
        enviar("/denuncias", "{\"motivo\":\"SPAM\",\"mensagensIds\":[" + ids.substring(0, ids.length() - 1) + "]}")
                .andExpect(status().isBadRequest());
        verify(service, never()).denunciar(any(), any(), any());
    }

    @Test
    @DisplayName("CT1082 - Pedido valido chega ao servico")
    void pedidoValido() throws Exception {
        when(service.denunciar(any(), any(), any())).thenReturn(new Confirmacao("Denúncia registrada."));
        enviar("/denuncias", "{\"motivo\":\"SPAM\",\"descricao\":\"x\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("CT1084 - CORS: sem SMARTRENT_CORS_ORIGENS nenhuma origem externa e liberada")
    void corsFechadoPorPadrao() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/auth/login")
                        .header("Origin", "https://site-malicioso.exemplo")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CT1083 - Cabecalhos de seguranca: CSP restritiva, nosniff, frame SAMEORIGIN, Referrer-Policy e Permissions-Policy")
    void cabecalhos() throws Exception {
        mockMvc.perform(get("/api/smartchat/nao-lidas").header("Authorization", "Bearer tk"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("object-src 'none'"),
                                org.hamcrest.Matchers.containsString("frame-ancestors 'self'"),
                                org.hamcrest.Matchers.containsString("base-uri 'self'"),
                                org.hamcrest.Matchers.containsString("form-action 'self'"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("geolocation=()")));
    }
}
