package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.LoteResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.AdminService;
import br.com.unisenai.smartrent.service.ComunicadoService;
import br.com.unisenai.smartrent.service.ModeracaoService;
import br.com.unisenai.smartrent.service.TelemetriaAnaliseService;
import br.com.unisenai.smartrent.service.TelemetriaService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O painel de admin e so de ADMIN (gestor tambem leva 403); a coleta de telemetria e publica mas respeita DNT/GPC. */
@WebMvcTest({AdminController.class, TelemetriaController.class, ModeracaoController.class, ComunicadoController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AdminService admin;
    @MockBean
    private TelemetriaAnaliseService analise;
    @MockBean
    private TelemetriaService coleta;
    @MockBean
    private ModeracaoService moderacao;
    @MockBean
    private ComunicadoService comunicados;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UsuarioRepository usuarioRepository;

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
    @DisplayName("CT1115 - Rotas do admin: visitante 401, cliente 403, gestor 403 e admin 200; o servico so e chamado pelo admin")
    void soAdmin() throws Exception {
        String[] rotas = {"/api/admin/visao-geral", "/api/admin/usuarios", "/api/admin/imoveis", "/api/admin/reservas",
                "/api/admin/uso/resumo", "/api/admin/uso/paginas", "/api/admin/uso/mapa-de-calor?pagina=/imoveis.html",
                "/api/admin/uso/exportar"};
        for (String rota : rotas) {
            mockMvc.perform(get(rota)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(rota).header("Authorization", "Bearer token-cliente")).andExpect(status().isForbidden());
            mockMvc.perform(get(rota).header("Authorization", "Bearer token-gestor")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(admin, analise);

        mockMvc.perform(get("/api/admin/visao-geral?dias=7").header("Authorization", "Bearer token-admin")).andExpect(status().isOk());
        verify(admin).visaoGeral(7);
        mockMvc.perform(get("/api/admin/usuarios?busca=ana&papel=CLIENTE").header("Authorization", "Bearer token-admin")).andExpect(status().isOk());
        verify(admin).usuarios(eq("ana"), eq("CLIENTE"), any(), any());
        mockMvc.perform(get("/api/admin/uso/mapa-de-calor?pagina=/imoveis.html&dispositivo=MOBILE&dias=14")
                .header("Authorization", "Bearer token-admin")).andExpect(status().isOk());
        verify(analise).mapa("/imoveis.html", "MOBILE", 14);
    }

    @Test
    @DisplayName("CT1116 - Moderacao: visitante 401, cliente e gestor 403; so o admin chega ao servico, com o proprio usuario")
    void moderacao() throws Exception {
        String motivo = "{\"motivo\":\"Golpe confirmado\"}";
        String[] gets = {"/api/admin/moderacao/denuncias", "/api/admin/moderacao/denuncias/1", "/api/admin/moderacao/automatizadas",
                "/api/admin/moderacao/contas-suspensas", "/api/admin/moderacao/acoes", "/api/admin/moderacao/comunicados"};
        for (String rota : gets) {
            mockMvc.perform(get(rota)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(rota).header("Authorization", "Bearer token-cliente")).andExpect(status().isForbidden());
            mockMvc.perform(get(rota).header("Authorization", "Bearer token-gestor")).andExpect(status().isForbidden());
        }
        String[] posts = {"/api/admin/moderacao/usuarios/9/suspensao", "/api/admin/moderacao/usuarios/9/reativacao",
                "/api/admin/moderacao/usuarios/9/mensagens", "/api/admin/moderacao/denuncias/1/decisao", "/api/admin/moderacao/alertas/1/revisao"};
        for (String rota : posts) {
            mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(motivo)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(rota).header("Authorization", "Bearer token-gestor").contentType(MediaType.APPLICATION_JSON).content(motivo))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(moderacao);

        mockMvc.perform(post("/api/admin/moderacao/usuarios/9/suspensao").header("Authorization", "Bearer token-admin")
                .contentType(MediaType.APPLICATION_JSON).content(motivo)).andExpect(status().isOk());
        verify(moderacao).suspender(org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 3L), eq(9L), eq("Golpe confirmado"), any());
        mockMvc.perform(get("/api/admin/moderacao/denuncias/1").header("Authorization", "Bearer token-admin")).andExpect(status().isOk());
        verify(moderacao).denuncia(org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 3L), eq(1L));
    }

    @Test
    @DisplayName("CT1125 - Avisos ao usuario: visitante 401; qualquer papel logado le so os proprios")
    void avisosDoUsuario() throws Exception {
        mockMvc.perform(get("/api/comunicados")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/comunicados/nao-lidos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/comunicados/5/lido")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/comunicados").header("Authorization", "Bearer token-cliente")).andExpect(status().isOk());
        verify(comunicados).listar(org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 1L));
        mockMvc.perform(post("/api/comunicados/5/lido").header("Authorization", "Bearer token-gestor")).andExpect(status().isOk());
        verify(comunicados).marcarLido(org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 2L), eq(5L));
    }

    @Test
    @DisplayName("CT1117 - Coleta de telemetria: visitante pode enviar (papel VISITANTE); logado leva o papel do token; DNT e GPC nao gravam nada")
    void coleta() throws Exception {
        when(coleta.registrar(any(), anyString(), anyString())).thenReturn(new LoteResponse(1, 0));
        String lote = "{\"sessaoId\":\"sessao-1234-abcd\",\"dispositivo\":\"DESKTOP\",\"eventos\":[{\"tipo\":\"VISUALIZACAO\",\"pagina\":\"/imoveis.html\"}]}";

        mockMvc.perform(post("/api/telemetria/eventos").contentType(MediaType.APPLICATION_JSON).content(lote))
                .andExpect(status().isAccepted());
        verify(coleta).registrar(any(), eq("VISITANTE"), anyString());

        mockMvc.perform(post("/api/telemetria/eventos").header("Authorization", "Bearer token-cliente")
                .contentType(MediaType.APPLICATION_JSON).content(lote)).andExpect(status().isAccepted());
        verify(coleta).registrar(any(), eq("CLIENTE"), anyString());

        mockMvc.perform(post("/api/telemetria/eventos").header("DNT", "1")
                .contentType(MediaType.APPLICATION_JSON).content(lote)).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/telemetria/eventos").header("Sec-GPC", "1")
                .contentType(MediaType.APPLICATION_JSON).content(lote)).andExpect(status().isAccepted());
        verify(coleta, org.mockito.Mockito.times(2)).registrar(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("CT1152 - Admin autenticado: cada rota do painel e da moderacao repassa os parametros ao servico e responde 200")
    void adminChegaEmTodasAsRotas() throws Exception {
        String t = "Bearer token-admin";
        String json = "{\"decisao\":\"PROCEDENTE\",\"nota\":\"Justificativa valida\",\"motivo\":\"Motivo valido\"}";
        for (String rota : new String[]{"/api/admin/usuarios?busca=ana&papel=CLIENTE&pagina=1&tamanho=5", "/api/admin/imoveis?status=PUBLICADO",
                "/api/admin/reservas?status=CONFIRMADA", "/api/admin/uso/resumo?dias=7", "/api/admin/uso/paginas",
                "/api/admin/moderacao/denuncias?status=PENDENTE", "/api/admin/moderacao/automatizadas?status=ABERTO&dias=7",
                "/api/admin/moderacao/contas-suspensas", "/api/admin/moderacao/acoes?usuarioId=4", "/api/admin/moderacao/niveis",
                "/api/admin/moderacao/comunicados"}) {
            mockMvc.perform(get(rota).header("Authorization", t)).andExpect(status().isOk());
        }
        verify(admin).imoveis(null, "PUBLICADO", null, null);
        for (String rota : new String[]{"/api/admin/moderacao/denuncias/1/decisao", "/api/admin/moderacao/alertas/2/revisao",
                "/api/admin/moderacao/usuarios/9/reativacao", "/api/admin/moderacao/usuarios/9/mensagens"}) {
            mockMvc.perform(post(rota).header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
        }
        verify(moderacao).decidirDenuncia(any(), eq(1L), any(), any());
        verify(moderacao).revisarAlerta(any(), eq(2L), any());
        verify(moderacao).reativar(any(), eq(9L), eq("Motivo valido"), any());
        verify(moderacao).enviarMensagem(any(), eq(9L), any());
        verify(moderacao).historico(eq(4L), any(), any());
        // exportacao em fluxo, so admin: cabecalho de download e tipo conforme o formato
        mockMvc.perform(get("/api/admin/uso/exportar?formato=ndjson&dias=3").header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("eventos-uso.ndjson")));
        mockMvc.perform(get("/api/admin/uso/exportar").header("Authorization", t))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("eventos-uso.csv")));
    }
}
