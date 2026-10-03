package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cobranca de uma reserva. A chave de idempotencia e unica no banco: mesmo com
 * clique duplo, retry ou webhook repetido, a reserva nunca e cobrada duas vezes.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda = "BRL";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "chave_idempotencia", nullable = false, length = 100, unique = true)
    private String chaveIdempotencia;

    @Column(name = "ref_gateway", length = 80)
    private String refGateway;

    @Column(name = "motivo_recusa", length = 200)
    private String motivoRecusa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public Long getId() { return id; }
    public Reserva getReserva() { return reserva; }
    public void setReserva(Reserva reserva) { this.reserva = reserva; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }
    public StatusPagamento getStatus() { return status; }
    public void setStatus(StatusPagamento status) { this.status = status; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public void setChaveIdempotencia(String chave) { this.chaveIdempotencia = chave; }
    public String getRefGateway() { return refGateway; }
    public void setRefGateway(String refGateway) { this.refGateway = refGateway; }
    public String getMotivoRecusa() { return motivoRecusa; }
    public void setMotivoRecusa(String motivoRecusa) { this.motivoRecusa = motivoRecusa; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
