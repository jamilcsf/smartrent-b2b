package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.config.SecurityConfig;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.SugestaoPreco;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtAuthenticationFilter;
import br.com.unisenai.smartrent.security.JwtService;
import br.com.unisenai.smartrent.service.ImovelAcesso;
import br.com.unisenai.smartrent.service.SugestaoPrecoService;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A sugestao de preco grava e consulta a IA: nao pode ser publica nem servir a quem nao e dono do imovel. */
@WebMvcTest(SugestaoPrecoController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SugestaoPrecoAutorizacaoTest {

    private static final String URL = "/api/precificacao/sugerir?imovelId=7&dataRef=2026-12-20&valorBase=300";

    @Autowired private MockMvc mockMvc;
    @MockBean private SugestaoPrecoService servico;
    @MockBean private ImovelAcesso acesso;
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
    @DisplayName("CT1020 - Visitante recebe 401 e cliente 403; nada e gravado nem enviado a IA")
    void semPapelDeGestor() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", "Bearer token-cliente")).andExpect(status().isForbidden());
        verify(servico, never()).gerarSugestaoPreco(any(), any(), any());
    }

    @Test
    @DisplayName("CT1021 - Gestor que nao e dono do imovel recebe 403 e nada e gravado")
    void gestorDeOutroImovel() throws Exception {
        when(acesso.doGestor(any(), eq(7L))).thenThrow(new AcessoNegadoException("Você não tem permissão sobre este imóvel."));
        mockMvc.perform(get(URL).header("Authorization", "Bearer token-gestor")).andExpect(status().isForbidden());
        verify(servico, never()).gerarSugestaoPreco(any(), any(), any());
    }

    @Test
    @DisplayName("CT1022 - Valor base zero, negativo ou absurdo: 400 antes de qualquer acesso")
    void valorBaseInvalido() throws Exception {
        for (String v : new String[]{"0", "-5", "99999999"}) {
            mockMvc.perform(get("/api/precificacao/sugerir?imovelId=7&dataRef=2026-12-20&valorBase=" + v)
                    .header("Authorization", "Bearer token-gestor")).andExpect(status().isBadRequest());
        }
        verify(servico, never()).gerarSugestaoPreco(any(), any(), any());
    }

    @Test
    @DisplayName("CT1023 - Gestor dono do imovel recebe a sugestao")
    void gestorDono() throws Exception {
        Imovel imovel = new Imovel();
        imovel.setId(7L);
        when(acesso.doGestor(any(), eq(7L))).thenReturn(imovel);
        SugestaoPreco s = new SugestaoPreco();
        s.setImovel(imovel);
        s.setValorBase(new BigDecimal("300"));
        s.setValorSugerido(new BigDecimal("330"));
        when(servico.gerarSugestaoPreco(any(), any(), any())).thenReturn(s);
        mockMvc.perform(get(URL).header("Authorization", "Bearer token-gestor")).andExpect(status().isOk());
    }
}
