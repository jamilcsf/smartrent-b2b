package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.OrigemCalculoPreco;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sugestao de diaria gerada para um imovel numa data.
 *
 * <p>{@code origemCalculo} registra se o valor veio da IA ou da regra de
 * contingencia, atendendo a exigencia de auditabilidade do RF09.
 */
@Entity
@Table(name = "sugestoes_preco", indexes = {
        @Index(name = "idx_sugestoes_imovel_data", columnList = "imovel_id, data_referencia")
})
public class SugestaoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sugestao_imovel"))
    private Imovel imovel;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Column(name = "valor_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorBase;

    @Column(name = "valor_sugerido", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSugerido;

    @Column(name = "percentual_ajuste", precision = 5, scale = 2)
    private BigDecimal percentualAjuste;

    @Column(name = "fator_sazonalidade", precision = 5, scale = 2)
    private BigDecimal fatorSazonalidade;

    @Column(name = "fator_microgeografia", precision = 5, scale = 2)
    private BigDecimal fatorMicrogeografia;

    @Column(name = "justificativa_ia", columnDefinition = "TEXT")
    private String justificativaIa;

    @Column(name = "modelo_ia_utilizado", length = 80)
    private String modeloIaUtilizado;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_calculo", nullable = false, length = 30)
    private OrigemCalculoPreco origemCalculo = OrigemCalculoPreco.IA_GENERATIVA;

    @Column(name = "data_geracao", nullable = false, updatable = false)
    private LocalDateTime dataGeracao;

    public SugestaoPreco() {
    }

    @PrePersist
    protected void aoPersistir() {
        this.dataGeracao = LocalDateTime.now();
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

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public void setDataReferencia(LocalDate dataReferencia) {
        this.dataReferencia = dataReferencia;
    }

    public BigDecimal getValorBase() {
        return valorBase;
    }

    public void setValorBase(BigDecimal valorBase) {
        this.valorBase = valorBase;
    }

    public BigDecimal getValorSugerido() {
        return valorSugerido;
    }

    public void setValorSugerido(BigDecimal valorSugerido) {
        this.valorSugerido = valorSugerido;
    }

    public BigDecimal getPercentualAjuste() {
        return percentualAjuste;
    }

    public void setPercentualAjuste(BigDecimal percentualAjuste) {
        this.percentualAjuste = percentualAjuste;
    }

    public BigDecimal getFatorSazonalidade() {
        return fatorSazonalidade;
    }

    public void setFatorSazonalidade(BigDecimal fatorSazonalidade) {
        this.fatorSazonalidade = fatorSazonalidade;
    }

    public BigDecimal getFatorMicrogeografia() {
        return fatorMicrogeografia;
    }

    public void setFatorMicrogeografia(BigDecimal fatorMicrogeografia) {
        this.fatorMicrogeografia = fatorMicrogeografia;
    }

    public String getJustificativaIa() {
        return justificativaIa;
    }

    public void setJustificativaIa(String justificativaIa) {
        this.justificativaIa = justificativaIa;
    }

    public String getModeloIaUtilizado() {
        return modeloIaUtilizado;
    }

    public void setModeloIaUtilizado(String modeloIaUtilizado) {
        this.modeloIaUtilizado = modeloIaUtilizado;
    }

    public OrigemCalculoPreco getOrigemCalculo() {
        return origemCalculo;
    }

    public void setOrigemCalculo(OrigemCalculoPreco origemCalculo) {
        this.origemCalculo = origemCalculo;
    }

    public LocalDateTime getDataGeracao() {
        return dataGeracao;
    }

    public void setDataGeracao(LocalDateTime dataGeracao) {
        this.dataGeracao = dataGeracao;
    }
}
