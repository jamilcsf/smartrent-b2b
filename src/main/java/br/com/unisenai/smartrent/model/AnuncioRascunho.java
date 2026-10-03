package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Rascunho de edicao de um anuncio no ar. Guarda os campos do cadastro como
 * JSON e a ordem/capa das midias; o anuncio original permanece intacto ate a
 * confirmacao e e restaurado exatamente como estava se o rascunho for
 * descartado.
 */
@Entity
@Table(name = "anuncio_rascunhos")
public class AnuncioRascunho {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_rascunho_imovel"))
    private Imovel imovel;

    /** Campos do formulario serializados (AnuncioDados). */
    @Column(name = "dados", nullable = false, columnDefinition = "TEXT")
    private String dados;

    /** Ids das midias na ordem escolhida, separados por virgula. */
    @Column(name = "midia_ordem", columnDefinition = "TEXT")
    private String midiaOrdem;

    @Column(name = "capa_midia_id")
    private Long capaMidiaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    public AnuncioRascunho() {
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

    public String getDados() {
        return dados;
    }

    public void setDados(String dados) {
        this.dados = dados;
    }

    public String getMidiaOrdem() {
        return midiaOrdem;
    }

    public void setMidiaOrdem(String midiaOrdem) {
        this.midiaOrdem = midiaOrdem;
    }

    public Long getCapaMidiaId() {
        return capaMidiaId;
    }

    public void setCapaMidiaId(Long capaMidiaId) {
        this.capaMidiaId = capaMidiaId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
