package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Decide o resultado de um cancelamento, sempre no servidor e a partir do
 * snapshot da reserva (parametros da politica vigentes na criacao), nunca da
 * configuracao atual. A decisao e uma sequencia ordenada de regras: a primeira
 * que se aplica vence, e uma regra nova entra na lista sem reescrever o servico.
 *
 * <p>Ordem: cancelamento pelo gestor, reserva pendente, [ponto de extensao do
 * direito de arrependimento, desligado], prazo de antecedencia.
 */
@Service
public class RefundPolicyService {

    public enum Quem { CLIENTE, GESTOR, SISTEMA }

    public enum Regra {
        GESTOR_CANCELA, PENDENTE_SEM_COBRANCA, REEMBOLSO_INTEGRAL_ANTECEDENCIA, SEM_REEMBOLSO_PRAZO
    }

    public record Contexto(Reserva reserva, Quem quem, Instant agora) {
    }

    /** Resultado: regra aplicada, estado final da reserva e valor a devolver (zero se nao houver reembolso). */
    public record Decisao(Regra regra, StatusReserva statusFinal, boolean comReembolso, BigDecimal valor,
                          Instant reembolsoIntegralAte) {
    }

    /** Uma regra da cadeia: devolve a decisao se se aplica, vazio caso contrario. */
    @FunctionalInterface
    interface RegraDeCancelamento {
        Optional<Decisao> avaliar(Contexto c);
    }

    private final List<RegraDeCancelamento> regras;
    private final PoliticaCancelamentoProperties politica;
    private final ZoneId zona;

    public RefundPolicyService(PoliticaCancelamentoProperties politica, ZoneId zonaDaPlataforma) {
        this.politica = politica;
        this.zona = zonaDaPlataforma;
        this.regras = List.of(
                this::gestorCancela,
                this::pendenteSemCobranca,
                this::arrependimento,
                this::antecedencia);
    }

    public Decisao decidir(Contexto contexto) {
        for (RegraDeCancelamento regra : regras) {
            Optional<Decisao> d = regra.avaliar(contexto);
            if (d.isPresent()) {
                return d.get();
            }
        }
        throw new IllegalStateException("Nenhuma regra de cancelamento se aplicou a reserva " + contexto.reserva().getId());
    }

    /** Fim do prazo do reembolso integral, com os parametros gravados na reserva. */
    public Instant limiteDoReembolso(Reserva r) {
        return PrazoReembolso.limite(r.getDataCheckin(), politica.checkinHora(), r.getPoliticaAntecedenciaHoras(), zona);
    }

    // ---------------------------------------------------------------- regras

    private Optional<Decisao> gestorCancela(Contexto c) {
        if (c.quem() != Quem.GESTOR) {
            return Optional.empty();
        }
        Reserva r = c.reserva();
        return Optional.of(new Decisao(Regra.GESTOR_CANCELA, StatusReserva.CANCELADA_PELO_GESTOR, true,
                r.getTotalSnapshot(), limiteDoReembolso(r)));
    }

    private Optional<Decisao> pendenteSemCobranca(Contexto c) {
        Reserva r = c.reserva();
        if (r.getStatus() != StatusReserva.PENDENTE) {
            return Optional.empty();
        }
        return Optional.of(new Decisao(Regra.PENDENTE_SEM_COBRANCA, StatusReserva.CANCELADA_SEM_REEMBOLSO, false,
                BigDecimal.ZERO, limiteDoReembolso(r)));
    }

    /**
     * Ponto de extensao do direito de arrependimento (CANCEL_REGRET_DAYS). Item em
     * stand-by do setor juridico: com o padrao 0 e, por decisao de produto, tambem
     * com qualquer outro valor nesta etapa, nao altera nenhuma decisao.
     */
    private Optional<Decisao> arrependimento(Contexto c) {
        return Optional.empty();
    }

    private Optional<Decisao> antecedencia(Contexto c) {
        Reserva r = c.reserva();
        Instant limite = limiteDoReembolso(r);
        if (PrazoReembolso.dentroDoPrazo(c.agora(), limite)) {
            return Optional.of(new Decisao(Regra.REEMBOLSO_INTEGRAL_ANTECEDENCIA, StatusReserva.CANCELADA_COM_REEMBOLSO,
                    true, r.getTotalSnapshot(), limite));
        }
        return Optional.of(new Decisao(Regra.SEM_REEMBOLSO_PRAZO, StatusReserva.CANCELADA_SEM_REEMBOLSO, false,
                BigDecimal.ZERO, limite));
    }
}
