package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Pedido de troca de e-mail aguardando confirmacao. So existe um pendente por usuario (indice
 * unico parcial no banco); um novo pedido cancela o anterior. O token em si NUNCA e guardado:
 * so o hash. O e-mail da conta so muda quando o link e confirmado.
 */
@Entity
@Table(name = "troca_email")
public class TrocaEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "email_novo", nullable = false, length = 150)
    private String emailNovo;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    public TrocaEmail() {
    }

    public TrocaEmail(Long usuarioId, String emailNovo, String tokenHash, Instant criadaEm, Instant expiraEm) {
        this.usuarioId = usuarioId;
        this.emailNovo = emailNovo;
        this.tokenHash = tokenHash;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
    }

    public boolean pendente(Instant agora) {
        return usadoEm == null && canceladaEm == null && agora.isBefore(expiraEm);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEmailNovo() {
        return emailNovo;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public void setUsadoEm(Instant usadoEm) {
        this.usadoEm = usadoEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }

    public void setCanceladaEm(Instant canceladaEm) {
        this.canceladaEm = canceladaEm;
    }
}
