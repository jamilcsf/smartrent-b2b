package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Reserva de um imovel por um periodo. */
@Entity
@Table(name = "reservas", indexes = {
        @Index(name = "idx_reservas_imovel", columnList = "imovel_id"),
        @Index(name = "idx_reservas_periodo", columnList = "imovel_id, data_checkin, data_checkout")
})
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reserva_imovel"))
    private Imovel imovel;

    @Column(name = "hospede_nome", nullable = false, length = 120)
    private String hospedeNome;

    @Column(name = "hospede_email", nullable = false, length = 150)
    private String hospedeEmail;

    @Column(name = "hospede_telefone", length = 20)
    private String hospedeTelefone;

    @Column(name = "data_checkin", nullable = false)
    private LocalDate dataCheckin;

    @Column(name = "data_checkout", nullable = false)
    private LocalDate dataCheckout;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReserva status = StatusReserva.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemReserva origem = OrigemReserva.DIRETA;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Travamento otimista: protege contra reservas concorrentes no mesmo imovel. */
    @Version
    @Column(name = "versao")
    private Long versao;

    public Reserva() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Imovel getImovel() {
        return imovel;
    }

    public void setImovel(Imovel imovel) {
        this.imovel = imovel;
    }

    public String getHospedeNome() {
        return hospedeNome;
    }

    public void setHospedeNome(String hospedeNome) {
        this.hospedeNome = hospedeNome;
    }

    public String getHospedeEmail() {
        return hospedeEmail;
    }

    public void setHospedeEmail(String hospedeEmail) {
        this.hospedeEmail = hospedeEmail;
    }

    public String getHospedeTelefone() {
        return hospedeTelefone;
    }

    public void setHospedeTelefone(String hospedeTelefone) {
        this.hospedeTelefone = hospedeTelefone;
    }

    public LocalDate getDataCheckin() {
        return dataCheckin;
    }

    public void setDataCheckin(LocalDate dataCheckin) {
        this.dataCheckin = dataCheckin;
    }

    public LocalDate getDataCheckout() {
        return dataCheckout;
    }

    public void setDataCheckout(LocalDate dataCheckout) {
        this.dataCheckout = dataCheckout;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public void setStatus(StatusReserva status) {
        this.status = status;
    }

    public OrigemReserva getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemReserva origem) {
        this.origem = origem;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public Long getVersao() {
        return versao;
    }

    public void setVersao(Long versao) {
        this.versao = versao;
    }
}
