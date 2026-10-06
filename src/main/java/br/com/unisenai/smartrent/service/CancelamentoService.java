package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Simulacao;
import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.PagamentoRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.RefundPolicyService.Contexto;
import br.com.unisenai.smartrent.service.RefundPolicyService.Decisao;
import br.com.unisenai.smartrent.service.RefundPolicyService.Quem;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.AcessoOcultoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancelamento de reservas por cliente, gestor ou sistema. A decisao (regra,
 * valor, estado final) e calculada sempre no servidor, a partir do snapshot da
 * reserva, e gravada no instante da confirmacao: a simulacao mostrada antes e so
 * informativa. Cancelar libera as datas na hora (a consulta de conflito ignora
 * reservas canceladas) e nao toca o snapshot de preco.
 */
@Service
public class CancelamentoService {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoService.class);

    private final ReservaRepository reservaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ReembolsoService reembolsoService;
    private final RefundPolicyService politica;
    private final ImovelAcesso acesso;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final NotificadorEmail email;
    private final TextosPoliticas textos;
    private final ReservaClienteMapper mapper;
    private final ApplicationEventPublisher publicador;
    private final Clock clock;
    private final long expiraMinutos;

    public CancelamentoService(ReservaRepository reservaRepository, PagamentoRepository pagamentoRepository,
                               ReembolsoService reembolsoService, RefundPolicyService politica, ImovelAcesso acesso,
                               AuditoriaService auditoria, NotificacaoService notificacoes, NotificadorEmail email,
                               TextosPoliticas textos, ReservaClienteMapper mapper,
                               ApplicationEventPublisher publicador, Clock clock,
                               br.com.unisenai.smartrent.config.ReservaProperties reservaProps) {
        this.reservaRepository = reservaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.reembolsoService = reembolsoService;
        this.politica = politica;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.notificacoes = notificacoes;
        this.email = email;
        this.textos = textos;
        this.mapper = mapper;
        this.publicador = publicador;
        this.clock = clock;
        this.expiraMinutos = reservaProps.pendenteExpiraMinutos();
    }

    // -------------------------------------------------------------- simulacao

    /** O que aconteceria se o cliente cancelasse agora. Nao grava nada. */
    @Transactional(readOnly = true)
    public Simulacao simular(Usuario cliente, Long reservaId) {
        Reserva r = doCliente(cliente, reservaId, false);
        exigirCancelavel(r);
        Instant agora = clock.instant();
        Decisao d = politica.decidir(new Contexto(r, Quem.CLIENTE, agora));
        String mensagem = switch (d.regra()) {
            case PENDENTE_SEM_COBRANCA -> textos.get("simulacao.pendente");
            case REEMBOLSO_INTEGRAL_ANTECEDENCIA -> textos.get("simulacao.com-reembolso", formatar(d.valor()));
            default -> textos.get("simulacao.sem-reembolso");
        };
        return new Simulacao(r.getId(), d.regra().name(), d.comReembolso(), d.valor(), mensagem,
                d.reembolsoIntegralAte(), agora);
    }

    // -------------------------------------------------------------- cancelar

    @Transactional
    public Reserva cancelarPeloCliente(Usuario cliente, Long reservaId, String motivo) {
        Reserva r = doCliente(cliente, reservaId, true);
        if (r.getStatus().cancelada()) {
            return r; // clique duplo ou retry: ja cancelada, nada a repetir
        }
        exigirCancelavel(r);
        return cancelar(r, Quem.CLIENTE, cliente, limpar(motivo));
    }

    /** Cancelamento pelo gestor: motivo obrigatorio, reembolso sempre integral e auditoria. */
    @Transactional
    public Reserva cancelarPeloGestor(Usuario gestor, Long reservaId, String motivo) {
        acesso.exigirGestor(gestor);
        Reserva r = reservaRepository.findByIdParaAtualizar(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
        try {
            acesso.conferir(gestor, r.getImovel());
        } catch (AcessoOcultoException e) {
            throw new AcessoOcultoException("Reserva não encontrada.");
        } catch (AcessoNegadoException e) {
            throw new AcessoNegadoException("Você não tem permissão sobre esta reserva.");
        }
        String m = limpar(motivo);
        if (m == null || m.isBlank()) {
            throw new IllegalArgumentException("Informe o motivo do cancelamento.");
        }
        if (r.getStatus().cancelada()) {
            return r;
        }
        exigirCancelavel(r);
        return cancelar(r, Quem.GESTOR, gestor, m);
    }

    /** Cancela, sem cobranca, uma reserva pendente (bloqueio de datas pelo gestor, expiracao). */
    @Transactional
    public Reserva cancelarPendentePeloSistema(Reserva reserva, String motivo) {
        Reserva r = reservaRepository.findByIdParaAtualizar(reserva.getId()).orElse(reserva);
        if (r.getStatus() != StatusReserva.PENDENTE) {
            return r;
        }
        return cancelar(r, Quem.SISTEMA, null, motivo);
    }

    /** Job: pendentes autoatendidas nao pagas a tempo deixam de segurar as datas. */
    @Transactional
    public int expirarPendentes() {
        LocalDateTime limite = Agora.somar(Agora.de(clock), Duration.ofMinutes(expiraMinutos).negated(), clock);
        List<Reserva> vencidas = reservaRepository.findPendentesExpiradas(limite);
        vencidas.forEach(r -> cancelarPendentePeloSistema(r, textos.get("cancelamento.expirada")));
        return vencidas.size();
    }

    // ---------------------------------------------------------------- nucleo

    private Reserva cancelar(Reserva r, Quem quem, Usuario autor, String motivo) {
        Instant agora = clock.instant();
        Decisao d = politica.decidir(new Contexto(r, quem, agora));

        r.setStatus(d.statusFinal());
        r.setCanceladaEm(agora);
        r.setCanceladaPor(quem.name());
        r.setCancelamentoRegra(d.regra().name());
        r.setCancelamentoMotivo(motivo == null || motivo.length() <= 300 ? motivo : motivo.substring(0, 300));
        reservaRepository.save(r);

        // So ha o que estornar se a reserva foi paga pela plataforma.
        boolean paga = pagamentoRepository.findFirstByReservaIdAndStatus(r.getId(), StatusPagamento.APROVADO).isPresent();
        Reembolso reembolso = null;
        if (d.comReembolso() && paga && d.valor().signum() > 0) {
            reembolso = reembolsoService.abrir(r, d.valor());
            reembolsoService.tentar(reembolso); // falha aqui NAO desfaz o cancelamento
        }

        auditoria.acao(r.getImovel(), autor == null ? null : autor.getId(), "RESERVA_CANCELADA",
                "reserva=" + r.getId() + " por=" + quem + " regra=" + d.regra() + " valor=" + d.valor()
                        + " em=" + agora + " (" + mapper.emBrasilia(agora) + " Brasília)");
        avisar(r, quem, d, reembolso, motivo);
        publicador.publishEvent(new ReservaCanceladaEvent(r.getId()));
        return r;
    }

    private void avisar(Reserva r, Quem quem, Decisao d, Reembolso reembolso, String motivo) {
        String resultadoCliente = switch (d.regra()) {
            case GESTOR_CANCELA -> textos.get("cancelamento.gestor") + " Valor: R$ " + formatar(d.valor()) + ". "
                    + textos.get("reembolso.prazo");
            case REEMBOLSO_INTEGRAL_ANTECEDENCIA -> textos.get("cancelamento.cliente-com-reembolso")
                    + " Valor: R$ " + formatar(d.valor()) + ". " + textos.get("reembolso.prazo");
            case PENDENTE_SEM_COBRANCA -> quem == Quem.SISTEMA ? textos.get("cancelamento.expirada")
                    : textos.get("cancelamento.pendente");
            default -> textos.get("cancelamento.cliente-sem-reembolso");
        };
        String titulo = "Reserva #" + r.getId() + " cancelada";
        String link = "/reserva.html?id=" + r.getId();
        Long imovelId = r.getImovel().getId();
        try {
            if (r.getCliente() != null) {
                notificacoes.criar(r.getCliente(), imovelId, titulo, resultadoCliente, link);
                email.enviar(r.getCliente(), titulo, resultadoCliente);
            }
            Usuario gestor = r.getImovel().getUsuario();
            String doGestor = "A reserva #" + r.getId() + " de " + r.getHospedeNome() + " (" + r.getDataCheckin()
                    + " a " + r.getDataCheckout() + ") foi cancelada (" + quem.name().toLowerCase() + ")."
                    + (quem == Quem.CLIENTE ? " " + textos.get(d.comReembolso() ? "cancelamento.cliente-com-reembolso"
                    : "cancelamento.cliente-sem-reembolso") : "")
                    + " As datas foram liberadas.";
            notificacoes.criar(gestor, imovelId, titulo, doGestor, "/dashboard.html?aba=reservas&imovelId=" + imovelId);
            email.enviar(gestor, titulo, doGestor);
        } catch (RuntimeException e) {
            // Aviso nao pode desfazer o cancelamento nem o estorno.
            log.warn("Falha ao avisar sobre o cancelamento da reserva {}", r.getId(), e);
        }
    }

    // ---------------------------------------------------------------- apoio

    private Reserva doCliente(Usuario cliente, Long id, boolean travar) {
        Reserva r = (travar ? reservaRepository.findByIdParaAtualizar(id) : reservaRepository.findById(id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
        if (cliente == null || r.getCliente() == null || !r.getCliente().getId().equals(cliente.getId())) {
            throw new AcessoOcultoException("Reserva não encontrada.");
        }
        return r;
    }

    private static void exigirCancelavel(Reserva r) {
        if (r.getStatus() != StatusReserva.PENDENTE && r.getStatus() != StatusReserva.CONFIRMADA) {
            throw new TransicaoInvalidaException("Esta reserva não pode mais ser cancelada (situação: "
                    + r.getStatus() + ").");
        }
    }

    private static String limpar(String motivo) {
        return motivo == null ? null : Sanitizador.linha(motivo);
    }

    private static String formatar(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
