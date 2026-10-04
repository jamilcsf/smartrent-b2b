package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Imovel anunciado por um anfitriao. */
@Entity
@Table(name = "imoveis", indexes = {
        @Index(name = "idx_imoveis_usuario", columnList = "usuario_id"),
        @Index(name = "idx_imoveis_ativo", columnList = "ativo"),
        @Index(name = "idx_imoveis_status", columnList = "status")
})
public class Imovel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_imovel_usuario"))
    private Usuario usuario;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_imovel", nullable = false, length = 20)
    private TipoImovel tipoImovel;

    @Embedded
    private Endereco endereco;

    @Column(name = "capacidade_hospedes", nullable = false)
    private Integer capacidadeHospedes;

    @Column(name = "numero_quartos", nullable = false)
    private Integer numeroQuartos;

    @Column(name = "numero_banheiros", nullable = false)
    private Integer numeroBanheiros;

    /** Área útil em metros quadrados. Opcional: imóveis antigos não a informam. */
    @Column(name = "metragem_quadrada")
    private Integer metragemQuadrada;

    /**
     * Vagas de garagem. Nulo e zero são situações distintas e ambas válidas:
     * nulo é "não informado", zero é "não tem". A interface omite as duas.
     */
    @Column(name = "vagas_garagem")
    private Integer vagasGaragem;

    /** Nulo enquanto o gestor ainda nao definiu o preco (PRE_PUBLICACAO_SEM_PRECO). */
    @Column(name = "valor_diaria_base", precision = 10, scale = 2)
    private BigDecimal valorDiariaBase;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "imovel_comodidades",
            joinColumns = @JoinColumn(name = "imovel_id",
                    foreignKey = @ForeignKey(name = "fk_comodidade_imovel")))
    @Column(name = "comodidade", length = 60)
    private List<String> comodidades = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusAnuncio status = StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO;

    /** Instante da PRIMEIRA confirmacao de preco; nunca e reescrito. Ancora a janela de 24h. */
    @Column(name = "preco_primeira_confirmacao_em")
    private LocalDateTime precoPrimeiraConfirmacaoEm;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;

    @Column(name = "edicao_iniciada_em")
    private LocalDateTime edicaoIniciadaEm;

    /** PUBLICADO ou REPUBLICACAO_AGENDADA: para onde o descarte devolve o imovel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "edicao_estado_origem", length = 30)
    private StatusAnuncio edicaoEstadoOrigem;

    @Column(name = "republicar_original_em")
    private LocalDateTime republicarOriginalEm;

    @Column(name = "edicao_confirmada_em")
    private LocalDateTime edicaoConfirmadaEm;

    @Column(name = "republicar_em")
    private LocalDateTime republicarEm;

    /** Quantidade minima de diarias por reserva (padrao 1). */
    @Column(name = "minimo_diarias", nullable = false)
    private int minimoDiarias = 1;

    /** Taxa de limpeza em R$, cobrada uma vez por reserva (0 = sem taxa). */
    @Column(name = "taxa_limpeza", nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal taxaLimpeza = java.math.BigDecimal.ZERO;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    public Imovel() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCadastro = LocalDateTime.now(br.com.unisenai.smartrent.config.PlataformaTempo.zona());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public TipoImovel getTipoImovel() {
        return tipoImovel;
    }

    public void setTipoImovel(TipoImovel tipoImovel) {
        this.tipoImovel = tipoImovel;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public void setEndereco(Endereco endereco) {
        this.endereco = endereco;
    }

    public Integer getCapacidadeHospedes() {
        return capacidadeHospedes;
    }

    public void setCapacidadeHospedes(Integer capacidadeHospedes) {
        this.capacidadeHospedes = capacidadeHospedes;
    }

    public Integer getNumeroQuartos() {
        return numeroQuartos;
    }

    public void setNumeroQuartos(Integer numeroQuartos) {
        this.numeroQuartos = numeroQuartos;
    }

    public Integer getNumeroBanheiros() {
        return numeroBanheiros;
    }

    public void setNumeroBanheiros(Integer numeroBanheiros) {
        this.numeroBanheiros = numeroBanheiros;
    }

    public Integer getMetragemQuadrada() {
        return metragemQuadrada;
    }

    public void setMetragemQuadrada(Integer metragemQuadrada) {
        this.metragemQuadrada = metragemQuadrada;
    }

    public Integer getVagasGaragem() {
        return vagasGaragem;
    }

    public void setVagasGaragem(Integer vagasGaragem) {
        this.vagasGaragem = vagasGaragem;
    }

    public BigDecimal getValorDiariaBase() {
        return valorDiariaBase;
    }

    public void setValorDiariaBase(BigDecimal valorDiariaBase) {
        this.valorDiariaBase = valorDiariaBase;
    }

    public List<String> getComodidades() {
        return comodidades;
    }

    public void setComodidades(List<String> comodidades) {
        this.comodidades = comodidades;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public StatusAnuncio getStatus() {
        return status;
    }

    public void setStatus(StatusAnuncio status) {
        this.status = status;
    }

    public LocalDateTime getPrecoPrimeiraConfirmacaoEm() {
        return precoPrimeiraConfirmacaoEm;
    }

    public void setPrecoPrimeiraConfirmacaoEm(LocalDateTime precoPrimeiraConfirmacaoEm) {
        this.precoPrimeiraConfirmacaoEm = precoPrimeiraConfirmacaoEm;
    }

    public LocalDateTime getPublicadoEm() {
        return publicadoEm;
    }

    public void setPublicadoEm(LocalDateTime publicadoEm) {
        this.publicadoEm = publicadoEm;
    }

    public LocalDateTime getEdicaoIniciadaEm() {
        return edicaoIniciadaEm;
    }

    public void setEdicaoIniciadaEm(LocalDateTime edicaoIniciadaEm) {
        this.edicaoIniciadaEm = edicaoIniciadaEm;
    }

    public StatusAnuncio getEdicaoEstadoOrigem() {
        return edicaoEstadoOrigem;
    }

    public void setEdicaoEstadoOrigem(StatusAnuncio edicaoEstadoOrigem) {
        this.edicaoEstadoOrigem = edicaoEstadoOrigem;
    }

    public LocalDateTime getRepublicarOriginalEm() {
        return republicarOriginalEm;
    }

    public void setRepublicarOriginalEm(LocalDateTime republicarOriginalEm) {
        this.republicarOriginalEm = republicarOriginalEm;
    }

    public LocalDateTime getEdicaoConfirmadaEm() {
        return edicaoConfirmadaEm;
    }

    public void setEdicaoConfirmadaEm(LocalDateTime edicaoConfirmadaEm) {
        this.edicaoConfirmadaEm = edicaoConfirmadaEm;
    }

    public LocalDateTime getRepublicarEm() {
        return republicarEm;
    }

    public void setRepublicarEm(LocalDateTime republicarEm) {
        this.republicarEm = republicarEm;
    }

    public int getMinimoDiarias() {
        return minimoDiarias;
    }

    public void setMinimoDiarias(int minimoDiarias) {
        this.minimoDiarias = minimoDiarias;
    }

    public java.math.BigDecimal getTaxaLimpeza() {
        return taxaLimpeza;
    }

    public void setTaxaLimpeza(java.math.BigDecimal taxaLimpeza) {
        this.taxaLimpeza = taxaLimpeza;
    }
}
