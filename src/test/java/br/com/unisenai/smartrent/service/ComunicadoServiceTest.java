package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Comunicado;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.NivelRemetente;
import br.com.unisenai.smartrent.repository.ComunicadoRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O usuario so le e marca como lido os proprios avisos; aviso alheio responde como inexistente. */
class ComunicadoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private ComunicadoRepository repository;
    private ComunicadoService service;
    private Usuario dono;
    private Usuario outro;

    @BeforeEach
    void preparar() {
        repository = mock(ComunicadoRepository.class);
        service = new ComunicadoService(repository, RELOGIO);
        dono = usuario(1);
        outro = usuario(2);
    }

    private static Usuario usuario(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    private Comunicado aviso(long id, Usuario destino) {
        Comunicado c = new Comunicado(destino, usuario(9), NivelRemetente.SUPORTE, "Assunto " + id, "Texto " + id, null, RELOGIO.instant());
        when(repository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("CT1139 - Lista os proprios avisos com o rotulo do nivel e a marca de lido; conta os nao lidos")
    void lista() {
        Comunicado lido = new Comunicado(dono, usuario(9), NivelRemetente.MODERACAO, "Um", "Texto", null, RELOGIO.instant());
        lido.setLidoEm(RELOGIO.instant());
        Comunicado novo = new Comunicado(dono, usuario(9), NivelRemetente.SEGURANCA, "Dois", "Texto", null, RELOGIO.instant());
        when(repository.findTop50ByDestinatarioIdOrderByIdDesc(1L)).thenReturn(List.of(novo, lido));
        when(repository.countByDestinatarioIdAndLidoEmIsNull(1L)).thenReturn(1L);

        var lista = service.listar(dono);
        assertEquals(2, lista.size());
        assertEquals("Segurança e privacidade", lista.get(0).remetente());
        assertFalse(lista.get(0).lido());
        assertTrue(lista.get(1).lido());
        assertEquals(1, service.naoLidos(dono));
    }

    @Test
    @DisplayName("CT1140 - Marcar como lido: o dono marca (uma vez so); aviso de outra pessoa ou inexistente e 404 igual")
    void marcaLido() {
        Comunicado meu = aviso(10, dono);
        service.marcarLido(dono, 10L);
        assertEquals(RELOGIO.instant(), meu.getLidoEm());
        verify(repository).save(meu);

        org.mockito.Mockito.clearInvocations(repository);
        service.marcarLido(dono, 10L); // idempotente: nao regrava
        verify(repository, never()).save(any());

        aviso(11, outro);
        RecursoNaoEncontradoException alheio = assertThrows(RecursoNaoEncontradoException.class, () -> service.marcarLido(dono, 11L));
        when(repository.findById(12L)).thenReturn(Optional.empty());
        RecursoNaoEncontradoException inexistente = assertThrows(RecursoNaoEncontradoException.class, () -> service.marcarLido(dono, 12L));
        assertEquals(inexistente.getMessage(), alheio.getMessage(), "nao ha como descobrir avisos de terceiros");
    }
}
