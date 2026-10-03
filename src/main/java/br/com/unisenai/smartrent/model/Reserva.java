package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /*
     * Snapshot das condicoes no instante da criacao. Nenhuma destas colunas e
     * atualizavel: alterar o preco ou editar o anuncio depois nao muda o que
     * o cliente reservou nem o que paga. Calculos, cobranca, cancelamento,
     * reembolso e relatorios leem daqui, nunca do preco atual do imovel.
     */

    @Column(name = "moeda", nullable = false, length = 3, updatable = false)
    private String moeda = "BRL";

    @Column(name = "numero_diarias", nullable = false, updatable = false)
    private int numeroDiarias;

    @Column(name = "preco_diaria_snapshot", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal precoDiariaSnapshot;

    @Column(name = "taxas_snapshot", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal taxasSnapshot = BigDecimal.ZERO;

    @Column(name = "total_snapshot", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal totalSnapshot;

    @Column(name = "imovel_titulo_snapshot", nullable = false, length = 150, updatable = false)
    private String imovelTituloSnapshot;

    @Column(name = "imovel_endereco_snapshot", length = 400, updatable = false)
    private String imovelEnderecoSnapshot;

    @Column(name = "imovel_caracteristicas_snapshot", length = 500, updatable = false)
    private String imovelCaracteristicasSnapshot;

    /** Valor de cada diaria por data, gravado uma unica vez (o preco pode variar por data). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "reserva_precos_diarios",
            joinColumns = @JoinColumn(name = "reserva_id",
                    foreignKey = @ForeignKey(name = "fk_preco_diario_reserva")))
    @MapKeyColumn(name = "data")
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private Map<LocalDate, BigDecimal> precosDiarios = new LinkedHashMap<>();

    /** Travamento otimista: protege contra reservas concorrentes no mesmo imovel. */
    @Version
    @Column(name = "versao")
    private Long versao;

    public Reserva() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now(br.com.unisenai.smartrent.config.PlataformaTempo.zona());
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

    public String getMoeda() {
        return moeda;
    }

    public void setMoeda(String moeda) {
        this.moeda = moeda;
    }

    public int getNumeroDiarias() {
        return numeroDiarias;
    }

    public void setNumeroDiarias(int numeroDiarias) {
        this.numeroDiarias = numeroDiarias;
    }

    public BigDecimal getPrecoDiariaSnapshot() {
        return precoDiariaSnapshot;
    }

    public void setPrecoDiariaSnapshot(BigDecimal precoDiariaSnapshot) {
        this.precoDiariaSnapshot = precoDiariaSnapshot;
    }

    public BigDecimal getTaxasSnapshot() {
        return taxasSnapshot;
    }

    public void setTaxasSnapshot(BigDecimal taxasSnapshot) {
        this.taxasSnapshot = taxasSnapshot;
    }

    public BigDecimal getTotalSnapshot() {
        return totalSnapshot;
    }

    public void setTotalSnapshot(BigDecimal totalSnapshot) {
        this.totalSnapshot = totalSnapshot;
    }

    public String getImovelTituloSnapshot() {
        return imovelTituloSnapshot;
    }

    public void setImovelTituloSnapshot(String imovelTituloSnapshot) {
        this.imovelTituloSnapshot = imovelTituloSnapshot;
    }

    public String getImovelEnderecoSnapshot() {
        return imovelEnderecoSnapshot;
    }

    public void setImovelEnderecoSnapshot(String imovelEnderecoSnapshot) {
        this.imovelEnderecoSnapshot = imovelEnderecoSnapshot;
    }

    public String getImovelCaracteristicasSnapshot() {
        return imovelCaracteristicasSnapshot;
    }

    public void setImovelCaracteristicasSnapshot(String imovelCaracteristicasSnapshot) {
        this.imovelCaracteristicasSnapshot = imovelCaracteristicasSnapshot;
    }

    public Map<LocalDate, BigDecimal> getPrecosDiarios() {
        return precosDiarios;
    }

    public void setPrecosDiarios(Map<LocalDate, BigDecimal> precosDiarios) {
        this.precosDiarios = precosDiarios;
    }
}
