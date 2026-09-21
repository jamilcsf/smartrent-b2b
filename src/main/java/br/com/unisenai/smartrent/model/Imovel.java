package br.com.unisenai.smartrent.model;

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
        @Index(name = "idx_imoveis_ativo", columnList = "ativo")
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

    @Column(name = "valor_diaria_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDiariaBase;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "imovel_comodidades",
            joinColumns = @JoinColumn(name = "imovel_id",
                    foreignKey = @ForeignKey(name = "fk_comodidade_imovel")))
    @Column(name = "comodidade", length = 60)
    private List<String> comodidades = new ArrayList<>();

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    public Imovel() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataCadastro = LocalDateTime.now();
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
}
