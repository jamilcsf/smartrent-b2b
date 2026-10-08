package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.TipoAcaoModeracao;
import jakarta.persistence.*;

import java.time.Instant;

/** Linha imutavel da trilha de moderacao (V22). Nunca guarda conteudo de mensagem do SmartChat. */
@Entity
@Table(name = "moderacao_acoes")
public class AcaoModeracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Conta que sofreu a acao. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    /** Admin que agiu. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private Usuario admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, updatable = false)
    private TipoAcaoModeracao tipo;

    @Column(name = "motivo", length = 500, updatable = false)
    private String motivo;

    @Column(name = "denuncia_id", updatable = false)
    private Long denunciaId;

    @Column(name = "alerta_id", updatable = false)
    private Long alertaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public AcaoModeracao() {
    }

    public AcaoModeracao(Usuario usuario, Usuario admin, TipoAcaoModeracao tipo, String motivo, Long denunciaId,
                         Long alertaId, Instant criadoEm) {
        this.usuario = usuario;
        this.admin = admin;
        this.tipo = tipo;
        this.motivo = motivo == null || motivo.length() <= 500 ? motivo : motivo.substring(0, 500);
        this.denunciaId = denunciaId;
        this.alertaId = alertaId;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Usuario getAdmin() { return admin; }
    public TipoAcaoModeracao getTipo() { return tipo; }
    public String getMotivo() { return motivo; }
    public Long getDenunciaId() { return denunciaId; }
    public Long getAlertaId() { return alertaId; }
    public Instant getCriadoEm() { return criadoEm; }
}
