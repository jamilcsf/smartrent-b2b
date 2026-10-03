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

    @Column(name = "data_checkin", nullable = false)
    private LocalDate dataCheckin;

    @Column(name = "data_checkout", nullable = false)
    private LocalDate dataCheckout;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
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

    /** Termos vigentes na criacao (imutaveis): alterar o anuncio depois nao os muda. */
    @Column(name = "minimo_diarias_snapshot", nullable = false, updatable = false)
    private int minimoDiariasSnapshot = 1;

    @Column(name = "taxa_limpeza_snapshot", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal taxaLimpezaSnapshot = BigDecimal.ZERO;

    @Column(name = "limite_hospedes_snapshot", nullable = false, updatable = false)
    private int limiteHospedesSnapshot = 1;

    /** Politica de cancelamento vigente na criacao (imutavel). */
    @Column(name = "politica_antecedencia_horas", nullable = false, updatable = false)
    private int politicaAntecedenciaHoras = 48;

    @Column(name = "politica_regret_dias", nullable = false, updatable = false)
    private int politicaRegretDias = 0;

    @Column(name = "politica_versao", nullable = false, length = 30, updatable = false)
    private String politicaVersao = "PROVISORIA-1";

    /** Cliente (usuario) dono da reserva; nulo em reservas lancadas pelo gestor para hospede sem conta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_reserva_cliente"))
    private Usuario cliente;

    @Column(name = "numero_hospedes", nullable = false)
    private int numeroHospedes = 1;

    /** Cancelamento: instante (UTC), quem cancelou (CLIENTE, GESTOR, SISTEMA), regra aplicada e motivo. */
    @Column(name = "cancelada_em")
    private java.time.Instant canceladaEm;

    @Column(name = "cancelada_por", length = 20)
    private String canceladaPor;

    @Column(name = "cancelamento_regra", length = 40)
    private String cancelamentoRegra;

    @Column(name = "cancelamento_motivo", length = 300)
    private String cancelamentoMotivo;

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
        if (this.dataCriacao == null) { // os servicos gravam a hora do relogio injetavel; este e so o reserva
            this.dataCriacao = LocalDateTime.now(br.com.unisenai.smartrent.config.PlataformaTempo.zona());
        }
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

    public int getMinimoDiariasSnapshot() {
        return minimoDiariasSnapshot;
    }

    public void setMinimoDiariasSnapshot(int v) {
        this.minimoDiariasSnapshot = v;
    }

    public BigDecimal getTaxaLimpezaSnapshot() {
        return taxaLimpezaSnapshot;
    }

    public void setTaxaLimpezaSnapshot(BigDecimal v) {
        this.taxaLimpezaSnapshot = v;
    }

    public int getLimiteHospedesSnapshot() {
        return limiteHospedesSnapshot;
    }

    public void setLimiteHospedesSnapshot(int v) {
        this.limiteHospedesSnapshot = v;
    }

    public int getPoliticaAntecedenciaHoras() {
        return politicaAntecedenciaHoras;
    }

    public void setPoliticaAntecedenciaHoras(int v) {
        this.politicaAntecedenciaHoras = v;
    }

    public int getPoliticaRegretDias() {
        return politicaRegretDias;
    }

    public void setPoliticaRegretDias(int v) {
        this.politicaRegretDias = v;
    }

    public String getPoliticaVersao() {
        return politicaVersao;
    }

    public void setPoliticaVersao(String v) {
        this.politicaVersao = v;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public int getNumeroHospedes() {
        return numeroHospedes;
    }

    public void setNumeroHospedes(int numeroHospedes) {
        this.numeroHospedes = numeroHospedes;
    }

    public java.time.Instant getCanceladaEm() {
        return canceladaEm;
    }

    public void setCanceladaEm(java.time.Instant canceladaEm) {
        this.canceladaEm = canceladaEm;
    }

    public String getCanceladaPor() {
        return canceladaPor;
    }

    public void setCanceladaPor(String canceladaPor) {
        this.canceladaPor = canceladaPor;
    }

    public String getCancelamentoRegra() {
        return cancelamentoRegra;
    }

    public void setCancelamentoRegra(String cancelamentoRegra) {
        this.cancelamentoRegra = cancelamentoRegra;
    }

    public String getCancelamentoMotivo() {
        return cancelamentoMotivo;
    }

    public void setCancelamentoMotivo(String cancelamentoMotivo) {
        this.cancelamentoMotivo = cancelamentoMotivo;
    }
}
