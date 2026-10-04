package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Notificacao in-app ao gestor. */
@Entity
@Table(name = "notificacoes", indexes = @Index(name = "idx_notificacoes_usuario", columnList = "usuario_id, lida"))
public class Notificacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_notificacao_usuario"))
    private Usuario usuario;

    @Column(name = "imovel_id")
    private Long imovelId;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "mensagem", nullable = false, length = 600)
    private String mensagem;

    @Column(name = "link", length = 200)
    private String link;

    @Column(name = "lida", nullable = false)
    private boolean lida;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    public Notificacao() {
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

    public Long getImovelId() {
        return imovelId;
    }

    public void setImovelId(Long imovelId) {
        this.imovelId = imovelId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isLida() {
        return lida;
    }

    public void setLida(boolean lida) {
        this.lida = lida;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public void setCriadaEm(LocalDateTime criadaEm) {
        this.criadaEm = criadaEm;
    }
}
