package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoHistoricoRepository;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/** AccountRestrictionService: interruptor de configuracao, ponto unico de decisao e conclusao sem executor. */
class AccountRestrictionServiceTest {

    private static Usuario usuario(long id, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setPapel(papel);
        return u;
    }

    private static AccountRestrictionService servico(boolean habilitadas, boolean haPedido, AuditoriaContaService auditoria) {
        SolicitacaoExclusaoRepository repo = Mockito.mock(SolicitacaoExclusaoRepository.class);
        when(repo.existsByUsuarioIdAndEstadoIn(any(), anyCollection())).thenReturn(haPedido);
        return new AccountRestrictionService(repo, new ExclusaoDadosProperties(48, 30, "", habilitadas, 3),
                new TextosPoliticas(), auditoria);
    }

    @Test
    @DisplayName("CT498 - Com DATA_DELETION_RESTRICTIONS_ENABLED=false nenhuma restricao e aplicada, mesmo com pedido em andamento, e nada e auditado")
    void desligadaNaoRestringeNada() {
        AuditoriaContaService auditoria = Mockito.mock(AuditoriaContaService.class);
        AccountRestrictionService desligado = servico(false, true, auditoria);
        Usuario cliente = usuario(1L, PapelUsuario.CLIENTE);
        Usuario gestor = usuario(2L, PapelUsuario.ANFITRIAO);

        assertFalse(desligado.habilitadas());
        assertFalse(desligado.ativa(1L));
        assertTrue(desligado.restricoesAtivas(cliente).isEmpty());
        assertDoesNotThrow(() -> desligado.exigirPodeReservar(cliente));
        assertDoesNotThrow(() -> desligado.exigirAceitaNovasReservas(2L));
        assertDoesNotThrow(() -> desligado.exigirPodePublicar(gestor));
        assertDoesNotThrow(() -> desligado.exigirPodeConfirmarEdicao(gestor));
        assertDoesNotThrow(() -> desligado.exigirPodeAlterarEmail(gestor));
        assertFalse(desligado.gestorRestrito(2L));
        desligado.auditarInicio(1L, "ip");
        desligado.auditarFim(1L, "x", "ip");
        Mockito.verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("CT499 - Ligada e com pedido: cada papel tem as suas restricoes; sem pedido, nada")
    void ligadaRestringePorPapel() {
        AuditoriaContaService auditoria = Mockito.mock(AuditoriaContaService.class);
        AccountRestrictionService com = servico(true, true, auditoria);
        AccountRestrictionService sem = servico(true, false, auditoria);
        Usuario cliente = usuario(1L, PapelUsuario.CLIENTE);
        Usuario gestor = usuario(2L, PapelUsuario.ANFITRIAO);

        assertEquals(List.of("NOVA_RESERVA", "ALTERAR_EMAIL"), com.restricoesAtivas(cliente));
        assertEquals(List.of("ANUNCIOS_SEM_NOVAS_RESERVAS", "PUBLICAR_ANUNCIO", "CONFIRMAR_EDICAO", "ALTERAR_EMAIL"),
                com.restricoesAtivas(gestor));
        assertThrows(AcessoNegadoException.class, () -> com.exigirPodeReservar(cliente));
        assertThrows(AcessoNegadoException.class, () -> com.exigirPodePublicar(gestor));
        assertThrows(AcessoNegadoException.class, () -> com.exigirPodeConfirmarEdicao(gestor));
        assertThrows(AcessoNegadoException.class, () -> com.exigirPodeAlterarEmail(cliente));
        assertThrows(AcessoNegadoException.class, () -> com.exigirAceitaNovasReservas(2L));
        assertTrue(com.gestorRestrito(2L));

        assertTrue(sem.restricoesAtivas(cliente).isEmpty());
        assertDoesNotThrow(() -> sem.exigirPodeReservar(cliente));
        assertDoesNotThrow(() -> sem.exigirPodePublicar(gestor));
        assertFalse(sem.gestorRestrito(2L));

        com.auditarInicio(1L, "ip");
        Mockito.verify(auditoria).registrar(Mockito.eq(1L), Mockito.eq(AuditoriaContaService.RESTRICOES_INICIO), Mockito.anyString(), Mockito.eq("ip"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("CT500 - Concluir a exclusao sem DataDeletionExecutor registrado recusa e nao muda nada (ponto de extensao sem implementacao)")
    void concluirSemExecutor() {
        SolicitacaoExclusaoRepository repo = Mockito.mock(SolicitacaoExclusaoRepository.class);
        var s = new br.com.unisenai.smartrent.model.SolicitacaoExclusao(7L, "a@b.com", null, null, "ip",
                java.time.Instant.now(), java.time.Instant.now(), "h".repeat(64));
        s.setEstado(EstadoExclusao.APROVADA);
        when(repo.findByIdParaAtualizar(1L)).thenReturn(Optional.of(s));
        ObjectProvider<DataDeletionExecutor> semExecutor = Mockito.mock(ObjectProvider.class);
        when(semExecutor.getIfAvailable()).thenReturn(null);
        DataDeletionReviewService revisao = new DataDeletionReviewService(repo,
                Mockito.mock(SolicitacaoExclusaoHistoricoRepository.class), Mockito.mock(SinaisDeRisco.class),
                Mockito.mock(AuditoriaContaService.class), Mockito.mock(AccountRestrictionService.class), semExecutor,
                Clock.systemUTC());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> revisao.concluir(1L, 5L));
        assertTrue(e.getMessage().contains("DataDeletionExecutor"));
        assertEquals(EstadoExclusao.APROVADA, s.getEstado());
        Mockito.verify(repo, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("CT501 - A checagem e central: os fluxos afetados consultam o AccountRestrictionService e so ele conhece a solicitacao de exclusao")
    void checagemCentral() throws IOException {
        Path raiz = Path.of("src/main/java/br/com/unisenai/smartrent");
        // cada fluxo restrito passa pelo servico central
        assertContem(raiz, "service/ReservaClienteService.java", "restricoes.exigirPodeReservar", "restricoes.exigirAceitaNovasReservas");
        assertContem(raiz, "service/AnuncioService.java", "restricoes.exigirPodePublicar", "restricoes.habilitadas()");
        assertContem(raiz, "service/AnuncioEdicaoService.java", "restricoes.exigirPodeConfirmarEdicao");
        assertContem(raiz, "service/TrocaEmailService.java", "restricoes.exigirPodeAlterarEmail");
        assertContem(raiz, "service/CalendarioService.java", "restricoes.gestorRestrito");

        // e nenhum outro servico/controller de fluxo toca direto no repositorio das solicitacoes
        List<String> permitidos = List.of("AccountRestrictionService", "ExclusaoDadosService", "DataDeletionReviewService", "PerfilService");
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path p : arquivos.filter(f -> f.toString().endsWith(".java")).toList()) {
                String nome = p.getFileName().toString().replace(".java", "");
                if (nome.startsWith("SolicitacaoExclusao") || permitidos.contains(nome)) {
                    continue;
                }
                assertFalse(Files.readString(p).contains("SolicitacaoExclusaoRepository"),
                        nome + " nao deve consultar a solicitacao de exclusao direto: use o AccountRestrictionService");
            }
        }
    }

    private static void assertContem(Path raiz, String arquivo, String... trechos) throws IOException {
        String fonte = Files.readString(raiz.resolve(arquivo));
        for (String t : trechos) {
            assertTrue(fonte.contains(t), arquivo + " deveria chamar " + t);
        }
    }
}
