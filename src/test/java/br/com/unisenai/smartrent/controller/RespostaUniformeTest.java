package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.AcessoOcultoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 403 e 404 nao podem revelar se um id existe: recurso alheio responde exatamente como recurso inexistente. */
class RespostaUniformeTest {

    @RestController
    static class Falso {
        @GetMapping("/inexistente")
        String inexistente() {
            throw new RecursoNaoEncontradoException("Reserva não encontrada.");
        }

        @GetMapping("/alheio")
        String alheio() {
            throw new AcessoOcultoException("Reserva não encontrada.");
        }

        @GetMapping("/papel")
        String papel() {
            throw new AcessoNegadoException("Apenas gestores de imóveis podem executar esta operação.");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new Falso()).setControllerAdvice(new TratadorDeErros()).build();
    }

    @Test
    @DisplayName("CT540 - Recurso de outra pessoa e recurso inexistente: mesmo status e mesmo corpo (404)")
    void alheioIgualInexistente() throws Exception {
        String inexistente = mvc.perform(get("/inexistente")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String alheio = mvc.perform(get("/alheio")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(inexistente, alheio);
    }

    @Test
    @DisplayName("CT541 - Falta de papel continua 403 (nao depende de nenhum registro existir)")
    void papelContinua403() throws Exception {
        mvc.perform(get("/papel")).andExpect(status().isForbidden());
    }
}
