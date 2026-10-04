package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Estorno de uma reserva cancelada. Unico por reserva e com chave de
 * idempotencia propria: clique duplo, retry ou webhook repetido nunca estornam
 * duas vezes. Falha no gateway nao desfaz o cancelamento: o estorno fica
 * FALHA e e retentado com espera crescente.
 */
@Entity
@Table(name = "reembolsos")
public class Reembolso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReembolso status = StatusReembolso.PENDENTE;

    @Column(name = "chave_idempotencia", nullable = false, length = 100, unique = true)
    private String chaveIdempotencia;

    @Column(name = "ref_gateway", length = 80)
    private String refGateway;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "erro", length = 300)
    private String erro;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public Long getId() { return id; }
    public Reserva getReserva() { return reserva; }
    public void setReserva(Reserva reserva) { this.reserva = reserva; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public StatusReembolso getStatus() { return status; }
    public void setStatus(StatusReembolso status) { this.status = status; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public void setChaveIdempotencia(String chave) { this.chaveIdempotencia = chave; }
    public String getRefGateway() { return refGateway; }
    public void setRefGateway(String refGateway) { this.refGateway = refGateway; }
    public int getTentativas() { return tentativas; }
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }
    public Instant getProximaTentativaEm() { return proximaTentativaEm; }
    public void setProximaTentativaEm(Instant v) { this.proximaTentativaEm = v; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
