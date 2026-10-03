package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Trilha de auditoria das acoes sobre um anuncio (transicoes, descartes, precos). */
@Entity
@Table(name = "auditoria_anuncio", indexes = @Index(name = "idx_auditoria_imovel", columnList = "imovel_id, data_hora"))
public class AuditoriaAnuncio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_auditoria_imovel"))
    private Imovel imovel;

    /** Nulo quando a acao parte do sistema (jobs). */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "acao", nullable = false, length = 40)
    private String acao;

    @Column(name = "detalhes", length = 500)
    private String detalhes;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    public AuditoriaAnuncio() {
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

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public void setDetalhes(String detalhes) {
        this.detalhes = detalhes;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }
}
