package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Pedido de bloqueio de usuario registrado no SmartChat. PROTOTIPO: sem efeito
 * funcional enquanto {@code smartrent.chat.bloqueio-ativo=false}
 * (SMARTCHAT_BLOCK_ENFORCEMENT); o ponto unico de aplicacao futura e o
 * {@code SmartChatService}.
 */
@Entity
@Table(name = "smartchat_bloqueios_usuario")
public class BloqueioUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversa_id")
    private SmartChatConversa conversa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloqueador_id", nullable = false)
    private Usuario bloqueador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloqueado_id", nullable = false)
    private Usuario bloqueado;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "REGISTRADO";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Long getId() { return id; }
    public SmartChatConversa getConversa() { return conversa; }
    public void setConversa(SmartChatConversa conversa) { this.conversa = conversa; }
    public Usuario getBloqueador() { return bloqueador; }
    public void setBloqueador(Usuario bloqueador) { this.bloqueador = bloqueador; }
    public Usuario getBloqueado() { return bloqueado; }
    public void setBloqueado(Usuario bloqueado) { this.bloqueado = bloqueado; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
