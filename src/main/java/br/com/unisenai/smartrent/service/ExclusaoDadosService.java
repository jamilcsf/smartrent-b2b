package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.dto.ExclusaoDadosDtos.Solicitacao;
import br.com.unisenai.smartrent.dto.ExclusaoDadosDtos.Status;
import br.com.unisenai.smartrent.model.SolicitacaoExclusao;
import br.com.unisenai.smartrent.model.SolicitacaoExclusaoHistorico;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoHistoricoRepository;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Pedido de exclusao de dados do proprio usuario. NADA e excluido aqui: o pedido registra a
 * solicitacao (PENDENTE), avisa o usuario e a equipe e aciona as restricoes temporarias; a
 * analise e a decisao sao da equipe ({@link DataDeletionReviewService}).
 *
 * <p>Antifraude: exige reautenticacao, e-mail ao endereco cadastrado com link "nao fui eu"
 * (cancela sem login), periodo minimo de analise antes de qualquer aprovacao, sinais de risco
 * para a equipe, limite de pedidos e auditoria com IP. Os e-mails saem depois do commit e a
 * falha de envio nunca desfaz o pedido.
 */
@Service
public class ExclusaoDadosService {

    private static final Logger log = LoggerFactory.getLogger(ExclusaoDadosService.class);
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MOTIVO_MAX = 500;

    private final SolicitacaoExclusaoRepository repository;
    private final SolicitacaoExclusaoHistoricoRepository historico;
    private final PerfilService perfilService;
    private final ReautenticacaoService reautenticacao;
    private final LimitadorDeTaxa limitador;
    private final AuditoriaContaService auditoria;
    private final AccountRestrictionService restricoes;
    private final SinaisDeRisco sinais;
    private final EmailSender emailSender;
    private final TextosPoliticas textos;
    private final ExclusaoDadosProperties props;
    private final PerfilProperties perfilProps;
    private final Clock clock;

    public ExclusaoDadosService(SolicitacaoExclusaoRepository repository, SolicitacaoExclusaoHistoricoRepository historico,
                                PerfilService perfilService, ReautenticacaoService reautenticacao, LimitadorDeTaxa limitador,
                                AuditoriaContaService auditoria, AccountRestrictionService restricoes, SinaisDeRisco sinais,
                                EmailSender emailSender, TextosPoliticas textos, ExclusaoDadosProperties props,
                                PerfilProperties perfilProps, Clock clock) {
        this.repository = repository;
        this.historico = historico;
        this.perfilService = perfilService;
        this.reautenticacao = reautenticacao;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.restricoes = restricoes;
        this.sinais = sinais;
        this.emailSender = emailSender;
        this.textos = textos;
        this.props = props;
        this.perfilProps = perfilProps;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public Status status(Usuario sessao) {
        Optional<SolicitacaoExclusao> atual = repository.findFirstByUsuarioIdAndEstadoIn(sessao.getId(),
                EstadoExclusao.estadosEmAndamento());
        Solicitacao resumo = atual.map(s -> new Solicitacao(s.getEstado().name(), s.getCriadaEm(), true)).orElse(null);
        return new Status(resumo, textos.get("perfil.exclusao.modal.texto", props.minAnaliseHoras()),
                restricoes.descricoesDoPapel(sessao.getPapel()), restricoes.restricoesAtivas(sessao), props.minAnaliseHoras());
    }

    // -------------------------------------------------------------------- pedido

    @Transactional
    public Status solicitar(Usuario sessao, String motivoInformado, String senhaAtual, String credencialGoogle, String ip) {
        if (!limitador.permitir("exclusao:" + sessao.getId(), props.pedidosPorDia(), Duration.ofDays(1))) {
            throw new LimiteExcedidoException("Muitos pedidos de exclusão em pouco tempo. Tente novamente amanhã.");
        }
        Usuario u = perfilService.carregar(sessao);
        reautenticacao.exigir(u, senhaAtual, credencialGoogle, "pedido de exclusao de dados", ip);
        String motivo = motivo(motivoInformado);
        if (repository.existsByUsuarioIdAndEstadoIn(u.getId(), EstadoExclusao.estadosEmAndamento())) {
            throw new TransicaoInvalidaException("Você já tem uma solicitação de exclusão de dados em andamento.");
        }

        Instant agora = clock.instant();
        String token = TokenSeguro.gerar();
        String resumoSinais = sinais.resumo(u.getId(), ip); // antes de auditar o proprio pedido
        SolicitacaoExclusao s = new SolicitacaoExclusao(u.getId(), u.getEmail(), motivo, resumoSinais, ip, agora,
                agora.plus(Duration.ofHours(props.minAnaliseHoras())), TokenSeguro.hash(token));
        try {
            repository.saveAndFlush(s); // o indice unico parcial e a ultima barreira contra pedidos simultaneos
        } catch (DataIntegrityViolationException e) {
            throw new TransicaoInvalidaException("Você já tem uma solicitação de exclusão de dados em andamento.");
        }
        historico.save(new SolicitacaoExclusaoHistorico(s.getId(), null, EstadoExclusao.PENDENTE, agora, null, null));
        auditoria.registrar(u.getId(), AuditoriaContaService.EXCLUSAO_SOLICITADA, "solicitacao #" + s.getId() + " registrada", ip);
        restricoes.auditarInicio(u.getId(), ip);

        avisar(u, s, token, resumoSinais, agora);
        return status(u);
    }

    // ------------------------------------------------------------- cancelamento

    /** O proprio usuario cancela (enquanto nao concluida). Nao exige senha: e o caminho seguro, que so remove restricoes. */
    @Transactional
    public Status cancelar(Usuario sessao, String ip) {
        Usuario u = perfilService.carregar(sessao);
        SolicitacaoExclusao s = repository.findFirstByUsuarioIdAndEstadoIn(u.getId(), EstadoExclusao.estadosEmAndamento())
                .orElseThrow(() -> new TransicaoInvalidaException("Não há solicitação de exclusão de dados em andamento."));
        encerrarComoCancelada(repository.findByIdParaAtualizar(s.getId()).orElse(s), "cancelada pelo usuario no perfil", ip);
        return status(u);
    }

    /**
     * Link "Nao fui eu / cancelar solicitacao" do e-mail. Sem login: o token de uso unico e a prova.
     * O link abre uma pagina estatica que faz POST (um leitor automatico de e-mail que apenas abre
     * o link nao cancela nada). Mensagem unica para link invalido, usado ou de pedido ja encerrado.
     */
    @Transactional
    public String cancelarPorLink(String token, String ip) {
        if (!limitador.permitir("exclusao-link:" + ip, 20, Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitas tentativas. Tente novamente mais tarde.");
        }
        SolicitacaoExclusao s = token == null || token.isBlank() || token.length() > 200 ? null
                : repository.findPorHashParaAtualizar(TokenSeguro.hash(token.trim())).filter(x -> x.getEstado().emAndamento()).orElse(null);
        if (s == null) {
            throw new IllegalArgumentException("Este link já foi usado ou a solicitação não está mais em andamento.");
        }
        encerrarComoCancelada(s, "cancelada pelo link do e-mail (nao fui eu)", ip);
        return textos.get("perfil.exclusao.cancelada-por-link");
    }

    private void encerrarComoCancelada(SolicitacaoExclusao s, String observacao, String ip) {
        Instant agora = clock.instant();
        EstadoExclusao anterior = s.getEstado();
        s.setEstado(EstadoExclusao.CANCELADA_PELO_USUARIO);
        s.setDecididaEm(agora);
        repository.save(s);
        historico.save(new SolicitacaoExclusaoHistorico(s.getId(), anterior, EstadoExclusao.CANCELADA_PELO_USUARIO, agora, null, observacao));
        auditoria.registrar(s.getUsuarioId(), AuditoriaContaService.EXCLUSAO_CANCELADA, "solicitacao #" + s.getId() + ": " + observacao, ip);
        restricoes.auditarFim(s.getUsuarioId(), "solicitacao cancelada", ip);
    }

    // ------------------------------------------------------------------- e-mails

    private void avisar(Usuario u, SolicitacaoExclusao s, String token, String resumoSinais, Instant agora) {
        String nome = u.getNome();
        String email = u.getEmail();
        String quando = QUANDO.format(LocalDateTime.now(clock));
        String lista = String.join("\n", restricoes.descricoesDoPapel(u.getPapel()).stream().map(t -> "- " + t).toList());
        String link = perfilProps.baseUrl().replaceAll("/+$", "") + "/cancelar-exclusao.html?token=" + token;
        String papel = u.getPapel().name();
        Long solicitacaoId = s.getId();
        Long usuarioId = u.getId();
        Instant liberada = s.getAnaliseApos();

        AposCommit.executar("e-mail de confirmacao da solicitacao de exclusao", () -> emailSender.enviar(email,
                textos.get("perfil.exclusao.email-usuario.assunto"),
                textos.get("perfil.exclusao.email-usuario.corpo", nome, quando,
                        props.restricoesAtivas() ? lista : "(nenhuma)", link)));
        AposCommit.executar("notificacao da equipe sobre exclusao de dados", () -> {
            String destino = props.notificarEmail();
            String assunto = textos.get("perfil.exclusao.email-equipe.assunto", solicitacaoId);
            String corpo = textos.get("perfil.exclusao.email-equipe.corpo", solicitacaoId, usuarioId, papel, agora, liberada, resumoSinais);
            if (destino == null || destino.isBlank()) {
                log.warn("DATA_DELETION_NOTIFY_EMAIL nao configurado: solicitacao de exclusao #{} registrada sem e-mail a equipe.", solicitacaoId);
            } else {
                emailSender.enviar(destino, assunto, corpo);
            }
        });
    }

    private static String motivo(String bruto) {
        if (bruto == null) {
            return null;
        }
        String limpo = Sanitizador.texto(bruto);
        if (limpo.isEmpty()) {
            return null;
        }
        if (limpo.length() > MOTIVO_MAX) {
            throw new IllegalArgumentException("O motivo deve ter no máximo " + MOTIVO_MAX + " caracteres.");
        }
        return limpo;
    }

    /** Estados em andamento, para quem precisa listar (testes e futuro modulo de administracao). */
    List<EstadoExclusao> emAndamento() {
        return List.copyOf(EstadoExclusao.estadosEmAndamento());
    }
}
