package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import br.com.unisenai.smartrent.repository.PagamentoRepository;
import br.com.unisenai.smartrent.repository.ReembolsoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Estorno pelo mesmo gateway da cobranca, com chave de idempotencia por reserva.
 *
 * <p>Falha no gateway nunca desfaz o cancelamento: o reembolso fica FALHA, o erro
 * e gravado, ha nova tentativa automatica com espera crescente e o gestor recebe
 * um alerta (in-app e log) na primeira falha e quando as tentativas se esgotam.
 */
@Service
public class ReembolsoService {

    public static final int MAX_TENTATIVAS = 8;
    private static final Logger log = LoggerFactory.getLogger(ReembolsoService.class);

    private final ReembolsoRepository repository;
    private final PagamentoRepository pagamentoRepository;
    private final GatewayPagamento gateway;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    public ReembolsoService(ReembolsoRepository repository, PagamentoRepository pagamentoRepository,
                            GatewayPagamento gateway, NotificacaoService notificacoes, Clock clock) {
        this.repository = repository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
        this.notificacoes = notificacoes;
        this.clock = clock;
    }

    /** Abre o reembolso da reserva (um so por reserva: repetir devolve o existente). */
    @Transactional
    public Reembolso abrir(Reserva reserva, BigDecimal valor) {
        return repository.findByReservaId(reserva.getId()).orElseGet(() -> {
            Instant agora = clock.instant();
            Reembolso r = new Reembolso();
            r.setReserva(reserva);
            r.setValor(valor);
            r.setStatus(StatusReembolso.PENDENTE);
            r.setChaveIdempotencia("reserva:" + reserva.getId() + ":reembolso");
            r.setCriadoEm(agora);
            r.setAtualizadoEm(agora);
            return repository.save(r);
        });
    }

    /** Uma tentativa de estorno. Nunca lanca: o resultado fica no proprio registro. */
    @Transactional
    public void tentar(Reembolso reembolso) {
        if (reembolso.getStatus() == StatusReembolso.PROCESSADO) {
            return; // ja estornado: repetir nao estorna de novo
        }
        Instant agora = clock.instant();
        reembolso.setTentativas(reembolso.getTentativas() + 1);
        String refCobranca = pagamentoRepository
                .findFirstByReservaIdAndStatus(reembolso.getReserva().getId(), StatusPagamento.APROVADO)
                .map(p -> p.getRefGateway()).orElse(null);

        GatewayPagamento.Resultado resultado;
        try {
            resultado = gateway.estornar(reembolso.getChaveIdempotencia(), refCobranca, reembolso.getValor());
        } catch (RuntimeException e) {
            resultado = GatewayPagamento.Resultado.recusado("Gateway indisponível: " + e.getMessage());
        }

        reembolso.setAtualizadoEm(agora);
        if (resultado.aprovado()) {
            reembolso.setStatus(StatusReembolso.PROCESSADO);
            reembolso.setRefGateway(resultado.referencia());
            reembolso.setErro(null);
            reembolso.setProximaTentativaEm(null);
        } else {
            reembolso.setStatus(StatusReembolso.FALHA);
            String motivo = resultado.motivo() == null ? "Falha no estorno." : resultado.motivo();
            reembolso.setErro(motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
            long espera = Math.min(60, 1L << Math.min(reembolso.getTentativas(), 6));
            reembolso.setProximaTentativaEm(agora.plus(espera, ChronoUnit.MINUTES));
            alertar(reembolso);
        }
        repository.save(reembolso);
    }

    /** Retenta os estornos pendentes ou falhos (chamado pelo job). */
    @Transactional
    public int reprocessar() {
        List<Reembolso> lista = repository.findParaTentar(clock.instant(), MAX_TENTATIVAS);
        lista.forEach(this::tentar);
        return lista.size();
    }

    private void alertar(Reembolso r) {
        boolean primeira = r.getTentativas() == 1;
        boolean esgotou = r.getTentativas() >= MAX_TENTATIVAS;
        if (!primeira && !esgotou) {
            return;
        }
        Reserva reserva = r.getReserva();
        String texto = "Reembolso da reserva #" + reserva.getId() + " (R$ " + r.getValor()
                + ") " + (esgotou ? "não foi concluído após " + r.getTentativas() + " tentativas. Acompanhe manualmente."
                : "falhou e será tentado novamente automaticamente.");
        log.error("[alerta-reembolso] {} erro={}", texto, r.getErro());
        try {
            notificacoes.criar(reserva.getImovel().getUsuario(), reserva.getImovel().getId(),
                    "Reembolso com falha", texto, "/dashboard.html?aba=reservas&imovelId=" + reserva.getImovel().getId());
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel notificar o gestor sobre a falha do reembolso {}", r.getId(), e);
        }
    }
}
