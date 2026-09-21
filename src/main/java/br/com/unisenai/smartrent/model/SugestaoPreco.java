package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tb_sugestao_preco")
public class SugestaoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long imovelId;
    private LocalDate dataReferencia;
    private BigDecimal valorSugerido;
    private String origemCalculo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getImovelId() { return imovelId; }
    public void setImovelId(Long imovelId) { this.imovelId = imovelId; }
    public LocalDate getDataReferencia() { return dataReferencia; }
    public void setDataReferencia(LocalDate dataReferencia) { this.dataReferencia = dataReferencia; }
    public BigDecimal getValorSugerido() { return valorSugerido; }
    public void setValorSugerido(BigDecimal valorSugerido) { this.valorSugerido = valorSugerido; }
    public String getOrigemCalculo() { return origemCalculo; }
    public void setOrigemCalculo(String origemCalculo) { this.origemCalculo = origemCalculo; }
}