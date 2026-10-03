package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Registro de cada alteracao de preco: auditoria exigida pelo fluxo de anuncio. */
@Entity
@Table(name = "historico_preco", indexes = @Index(name = "idx_hist_preco_imovel", columnList = "imovel_id, data_hora"))
public class HistoricoPreco {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hist_preco_imovel"))
    private Imovel imovel;

    @Column(name = "valor_anterior", precision = 10, scale = 2)
    private BigDecimal valorAnterior;

    @Column(name = "valor_novo", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorNovo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hist_preco_autor"))
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 10)
    private OrigemPreco origem;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    public HistoricoPreco() {
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

    public BigDecimal getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(BigDecimal valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public BigDecimal getValorNovo() {
        return valorNovo;
    }

    public void setValorNovo(BigDecimal valorNovo) {
        this.valorNovo = valorNovo;
    }

    public Usuario getAutor() {
        return autor;
    }

    public void setAutor(Usuario autor) {
        this.autor = autor;
    }

    public OrigemPreco getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemPreco origem) {
        this.origem = origem;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }
}
