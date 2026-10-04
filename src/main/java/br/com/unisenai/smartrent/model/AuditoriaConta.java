package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Trilha de auditoria de eventos da conta (nome, foto, senha, e-mail, exclusao de dados,
 * inicio/fim de restricoes). Instante em UTC. Nunca guarda valores sensiveis: nem senha,
 * nem hash, nem token; so o evento e, quando util, um detalhe curto sem dados pessoais.
 */
@Entity
@Table(name = "auditoria_conta", indexes = @Index(name = "idx_auditoria_conta_usuario", columnList = "usuario_id, ocorrida_em"))
public class AuditoriaConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "acao", nullable = false, length = 40)
    private String acao;

    @Column(name = "detalhes", length = 500)
    private String detalhes;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "ocorrida_em", nullable = false, updatable = false)
    private Instant ocorridaEm;

    public AuditoriaConta() {
    }

    public AuditoriaConta(Long usuarioId, String acao, String detalhes, String ip, Instant ocorridaEm) {
        this.usuarioId = usuarioId;
        this.acao = acao;
        this.detalhes = detalhes == null || detalhes.length() <= 500 ? detalhes : detalhes.substring(0, 500);
        this.ip = ip == null || ip.length() <= 45 ? ip : ip.substring(0, 45);
        this.ocorridaEm = ocorridaEm;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getAcao() {
        return acao;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public String getIp() {
        return ip;
    }

    public Instant getOcorridaEm() {
        return ocorridaEm;
    }
}
