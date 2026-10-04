package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.SolicitacaoExclusao;
import br.com.unisenai.smartrent.model.SolicitacaoExclusaoHistorico;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoHistoricoRepository;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Analise das solicitacoes de exclusao de dados, para o FUTURO modulo de administracao: esta
 * etapa nao tem tela nem endpoint, so o servico e os testes. Quem chama informa o id do analista.
 *
 * <p>A aprovacao tem travas: o periodo minimo de analise precisa ter passado e nao pode haver
 * impedimentos (reserva futura ou em andamento, reembolso pendente, denuncia aberta contra o
 * usuario ou conversa com disputa); o servico devolve a lista dos impedimentos. As restricoes
 * temporarias continuam ate a conclusao; terminam ao negar, ao cancelar e ao concluir.
 */
@Service
public class DataDeletionReviewService {

    /** A aprovacao foi barrada por impedimentos objetivos (a lista vai junto, para a equipe). */
    public static class AprovacaoBloqueadaException extends RuntimeException {
        private final List<String> impedimentos;

        public AprovacaoBloqueadaException(String mensagem, List<String> impedimentos) {
            super(mensagem);
            this.impedimentos = List.copyOf(impedimentos);
        }

        public List<String> impedimentos() {
            return impedimentos;
        }
    }

    private final SolicitacaoExclusaoRepository repository;
    private final SolicitacaoExclusaoHistoricoRepository historico;
    private final SinaisDeRisco sinais;
    private final AuditoriaContaService auditoria;
    private final AccountRestrictionService restricoes;
    private final ObjectProvider<DataDeletionExecutor> executor;
    private final Clock clock;

    public DataDeletionReviewService(SolicitacaoExclusaoRepository repository,
                                     SolicitacaoExclusaoHistoricoRepository historico, SinaisDeRisco sinais,
                                     AuditoriaContaService auditoria, AccountRestrictionService restricoes,
                                     ObjectProvider<DataDeletionExecutor> executor, Clock clock) {
        this.repository = repository;
        this.historico = historico;
        this.sinais = sinais;
        this.auditoria = auditoria;
        this.restricoes = restricoes;
        this.executor = executor;
        this.clock = clock;
    }

    /** Impedimentos atuais da aprovacao (vazio = nada impede). */
    @Transactional(readOnly = true)
    public List<String> impedimentos(Long solicitacaoId) {
        return sinais.impedimentos(buscar(solicitacaoId).getUsuarioId());
    }

    /** PENDENTE -> EM_ANALISE. */
    @Transactional
    public void iniciarAnalise(Long solicitacaoId, Long analistaId) {
        SolicitacaoExclusao s = travar(solicitacaoId);
        exigirEstado(s, EstadoExclusao.PENDENTE);
        mudar(s, EstadoExclusao.EM_ANALISE, analistaId, null, false);
    }

    /** Aberta -> APROVADA, se o periodo minimo passou e nao ha impedimentos. */
    @Transactional
    public void aprovar(Long solicitacaoId, Long analistaId, String observacao) {
        SolicitacaoExclusao s = travar(solicitacaoId);
        if (!s.getEstado().aberta()) {
            throw new TransicaoInvalidaException("Só é possível aprovar uma solicitação pendente ou em análise.");
        }
        Instant agora = clock.instant();
        if (agora.isBefore(s.getAnaliseApos())) {
            throw new AprovacaoBloqueadaException("O período mínimo de análise ainda não terminou (libera em "
                    + s.getAnaliseApos() + " UTC).", List.of("PERIODO_MINIMO_DE_ANALISE"));
        }
        List<String> impedimentos = sinais.impedimentos(s.getUsuarioId());
        if (!impedimentos.isEmpty()) {
            throw new AprovacaoBloqueadaException("A aprovação está bloqueada por impedimentos.", impedimentos);
        }
        s.setObservacaoInterna(observacao);
        mudar(s, EstadoExclusao.APROVADA, analistaId, observacao, false);
    }

    /** Aberta ou aprovada -> NEGADA. Encerra as restricoes. */
    @Transactional
    public void negar(Long solicitacaoId, Long analistaId, String observacao) {
        SolicitacaoExclusao s = travar(solicitacaoId);
        if (!s.getEstado().emAndamento()) {
            throw new TransicaoInvalidaException("Só é possível negar uma solicitação em andamento.");
        }
        s.setObservacaoInterna(observacao);
        mudar(s, EstadoExclusao.NEGADA, analistaId, observacao, true);
    }

    /**
     * APROVADA -> CONCLUIDA, depois de executar a exclusao. SEM executor registrado (hoje nao ha),
     * recusa e nao toca em nada: a execucao e ponto de extensao dependente do juridico.
     */
    @Transactional
    public void concluir(Long solicitacaoId, Long analistaId) {
        SolicitacaoExclusao s = travar(solicitacaoId);
        exigirEstado(s, EstadoExclusao.APROVADA);
        DataDeletionExecutor exec = executor.getIfAvailable();
        if (exec == null) {
            throw new IllegalStateException("A execução da exclusão de dados ainda não foi implementada (DataDeletionExecutor).");
        }
        exec.executar(s.getUsuarioId());
        mudar(s, EstadoExclusao.CONCLUIDA, analistaId, null, true);
    }

    // ------------------------------------------------------------------ apoio

    private void mudar(SolicitacaoExclusao s, EstadoExclusao novo, Long analistaId, String observacao, boolean fimDasRestricoes) {
        Instant agora = clock.instant();
        EstadoExclusao anterior = s.getEstado();
        s.setEstado(novo);
        s.setAnalistaId(analistaId);
        if (novo != EstadoExclusao.EM_ANALISE) {
            s.setDecididaEm(agora);
        }
        repository.save(s);
        historico.save(new SolicitacaoExclusaoHistorico(s.getId(), anterior, novo, agora, analistaId, observacao));
        auditoria.registrar(s.getUsuarioId(), AuditoriaContaService.EXCLUSAO_DECIDIDA,
                "solicitacao #" + s.getId() + ": " + anterior + " -> " + novo, null);
        if (fimDasRestricoes) {
            restricoes.auditarFim(s.getUsuarioId(), "solicitacao " + novo, null);
        }
    }

    private static void exigirEstado(SolicitacaoExclusao s, EstadoExclusao esperado) {
        if (s.getEstado() != esperado) {
            throw new TransicaoInvalidaException("A solicitação está em " + s.getEstado() + "; era esperado " + esperado + ".");
        }
    }

    private SolicitacaoExclusao travar(Long id) {
        return repository.findByIdParaAtualizar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
    }

    private SolicitacaoExclusao buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada."));
    }
}
