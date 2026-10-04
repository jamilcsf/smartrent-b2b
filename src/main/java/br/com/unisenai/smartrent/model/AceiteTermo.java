package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Aceite do termo de uso: quem, qual imovel, qual versao, quando e de que IP. */
@Entity
@Table(name = "aceites_termo", indexes = @Index(name = "idx_aceites_imovel", columnList = "imovel_id"))
public class AceiteTermo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aceite_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aceite_imovel"))
    private Imovel imovel;

    @Column(name = "versao_termo", nullable = false, length = 20)
    private String versaoTermo;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", nullable = false, length = 30)
    private ContextoAceite contexto;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @Column(name = "ip", length = 45)
    private String ip;

    public AceiteTermo() {
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

    public Imovel getImovel() {
        return imovel;
    }

    public void setImovel(Imovel imovel) {
        this.imovel = imovel;
    }

    public String getVersaoTermo() {
        return versaoTermo;
    }

    public void setVersaoTermo(String versaoTermo) {
        this.versaoTermo = versaoTermo;
    }

    public ContextoAceite getContexto() {
        return contexto;
    }

    public void setContexto(ContextoAceite contexto) {
        this.contexto = contexto;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
