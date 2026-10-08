package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.NivelRemetente;
import jakarta.persistence.*;

import java.time.Instant;

/** Aviso da administracao da plataforma para um usuario (V22). So o destinatario le; so a leitura muda depois de gravado. */
@Entity
@Table(name = "comunicados")
public class Comunicado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_id", nullable = false, updatable = false)
    private Usuario destinatario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remetente_admin_id", nullable = false, updatable = false)
    private Usuario remetenteAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "remetente_nivel", nullable = false, length = 20, updatable = false)
    private NivelRemetente remetenteNivel;

    @Column(name = "assunto", nullable = false, length = 150, updatable = false)
    private String assunto;

    @Column(name = "texto", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String texto;

    @Column(name = "denuncia_id", updatable = false)
    private Long denunciaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "lido_em")
    private Instant lidoEm;

    public Comunicado() {
    }

    public Comunicado(Usuario destinatario, Usuario remetenteAdmin, NivelRemetente nivel, String assunto, String texto,
                      Long denunciaId, Instant criadoEm) {
        this.destinatario = destinatario;
        this.remetenteAdmin = remetenteAdmin;
        this.remetenteNivel = nivel;
        this.assunto = assunto;
        this.texto = texto;
        this.denunciaId = denunciaId;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public Usuario getDestinatario() { return destinatario; }
    public Usuario getRemetenteAdmin() { return remetenteAdmin; }
    public NivelRemetente getRemetenteNivel() { return remetenteNivel; }
    public String getAssunto() { return assunto; }
    public String getTexto() { return texto; }
    public Long getDenunciaId() { return denunciaId; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getLidoEm() { return lidoEm; }
    public void setLidoEm(Instant lidoEm) { this.lidoEm = lidoEm; }
}
