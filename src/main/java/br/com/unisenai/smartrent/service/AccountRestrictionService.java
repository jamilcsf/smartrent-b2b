package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.SolicitacaoExclusaoRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UNICO ponto de decisao das restricoes temporarias de uma conta com solicitacao de exclusao de
 * dados em andamento (PENDENTE, EM_ANALISE ou APROVADA). Os fluxos afetados (reserva, publicacao,
 * confirmacao de edicao, troca de e-mail, disponibilidade publica) chamam aqui e nao conhecem a
 * regra; assim ela nao se espalha pelo codigo.
 *
 * <p>A conta NAO e bloqueada por inteiro: so ficam restritas as acoes que servem de rota de fuga
 * para golpes. Nunca passam por aqui (nunca sao restritas): login, leitura, troca de senha,
 * cancelar a propria solicitacao, reservas confirmadas, cancelamentos e reembolsos (inclusive o
 * cancelamento do gestor com reembolso integral), descarte de edicao, edicao de rascunho em
 * pre-publicacao, bloqueio de datas e SmartChat. Desligavel por configuracao
 * (DATA_DELETION_RESTRICTIONS_ENABLED=false): com ela desligada, nada e restrito.
 */
@Service
public class AccountRestrictionService {

    /** Codigos estaveis (a interface e os textos usam estes nomes). */
    public enum Restricao {
        NOVA_RESERVA,
        ANUNCIOS_SEM_NOVAS_RESERVAS,
        PUBLICAR_ANUNCIO,
        CONFIRMAR_EDICAO,
        ALTERAR_EMAIL
    }

    private final SolicitacaoExclusaoRepository repository;
    private final ExclusaoDadosProperties props;
    private final TextosPoliticas textos;
    private final AuditoriaContaService auditoria;

    public AccountRestrictionService(SolicitacaoExclusaoRepository repository, ExclusaoDadosProperties props,
                                     TextosPoliticas textos, AuditoriaContaService auditoria) {
        this.repository = repository;
        this.props = props;
        this.textos = textos;
        this.auditoria = auditoria;
    }

    /** O usuario tem solicitacao em andamento E as restricoes estao habilitadas? */
    public boolean ativa(Long usuarioId) {
        return usuarioId != null && props.restricoesAtivas()
                && repository.existsByUsuarioIdAndEstadoIn(usuarioId, EstadoExclusao.estadosEmAndamento());
    }

    /** Restricoes que valem (ou valeriam) para o papel, em ordem de exibicao. */
    public static List<Restricao> restricoesDoPapel(PapelUsuario papel) {
        if (papel == PapelUsuario.CLIENTE) {
            return List.of(Restricao.NOVA_RESERVA, Restricao.ALTERAR_EMAIL);
        }
        if (papel == PapelUsuario.ANFITRIAO) {
            return List.of(Restricao.ANUNCIOS_SEM_NOVAS_RESERVAS, Restricao.PUBLICAR_ANUNCIO,
                    Restricao.CONFIRMAR_EDICAO, Restricao.ALTERAR_EMAIL);
        }
        return List.of(Restricao.ALTERAR_EMAIL);
    }

    /** Codigos das restricoes ativas agora (lista vazia quando nao ha). */
    public List<String> restricoesAtivas(Usuario usuario) {
        if (!ativa(usuario.getId())) {
            return List.of();
        }
        return restricoesDoPapel(usuario.getPapel()).stream().map(Restricao::name).toList();
    }

    /** Textos (provisorios, do arquivo unico) das restricoes de um papel, para o modal de pedido. */
    public List<String> descricoesDoPapel(PapelUsuario papel) {
        return restricoesDoPapel(papel).stream().map(r -> textos.get("perfil.restricao.descricao." + r.name())).toList();
    }

    // ------------------------------------------------------------------ verificacoes

    /** Cliente com pedido em andamento nao cria reservas novas. */
    public void exigirPodeReservar(Usuario cliente) {
        negarSeAtiva(cliente.getId(), Restricao.NOVA_RESERVA);
    }

    /**
     * Anuncio de gestor com pedido em andamento nao aceita novas reservas. A mensagem ao cliente
     * e generica de proposito: nao revela o motivo (nem que existe pedido de exclusao).
     */
    public void exigirAceitaNovasReservas(Long gestorId) {
        if (gestorRestrito(gestorId)) {
            throw new AcessoNegadoException(textos.get("perfil.restricao.imovel-indisponivel"));
        }
    }

    /** Usado pela disponibilidade publica: todo o periodo aparece como indisponivel, sem motivo. */
    public boolean gestorRestrito(Long gestorId) {
        return ativa(gestorId);
    }

    public void exigirPodePublicar(Usuario gestor) {
        negarSeAtiva(gestor.getId(), Restricao.PUBLICAR_ANUNCIO);
    }

    public void exigirPodeConfirmarEdicao(Usuario gestor) {
        negarSeAtiva(gestor.getId(), Restricao.CONFIRMAR_EDICAO);
    }

    public void exigirPodeAlterarEmail(Usuario usuario) {
        negarSeAtiva(usuario.getId(), Restricao.ALTERAR_EMAIL);
    }

    /** Variante por id, para quem so tem o id do dono (confirmacao do link de e-mail, jobs). */
    public void exigirPodeAlterarEmail(Long usuarioId) {
        negarSeAtiva(usuarioId, Restricao.ALTERAR_EMAIL);
    }

    private void negarSeAtiva(Long usuarioId, Restricao restricao) {
        if (ativa(usuarioId)) {
            throw new AcessoNegadoException(textos.get("perfil.restricao.msg." + restricao.name()));
        }
    }

    // ------------------------------------------------------------------ auditoria

    /** Registra o INICIO das restricoes (so quando estao habilitadas). */
    public void auditarInicio(Long usuarioId, String ip) {
        if (props.restricoesAtivas()) {
            auditoria.registrar(usuarioId, AuditoriaContaService.RESTRICOES_INICIO,
                    "restricoes temporarias iniciadas (solicitacao de exclusao de dados)", ip);
        }
    }

    /** Registra o FIM das restricoes (cancelamento, negativa ou conclusao). */
    public void auditarFim(Long usuarioId, String motivo, String ip) {
        if (props.restricoesAtivas()) {
            auditoria.registrar(usuarioId, AuditoriaContaService.RESTRICOES_FIM,
                    "restricoes temporarias encerradas: " + motivo, ip);
        }
    }
}
