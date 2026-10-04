package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Conversa do SmartChat. Unica por cliente + gestor + imovel (restricao no banco):
 * qualquer caminho (anuncio, reserva, confirmacao automatica) reutiliza a mesma.
 */
@Entity
@Table(name = "smartchat_conversas")
public class SmartChatConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestor_id", nullable = false)
    private Usuario gestor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false)
    private Imovel imovel;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "ultima_mensagem_em")
    private Instant ultimaMensagemEm;

    /** Reservas do cliente naquele imovel ligadas a esta conversa. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "smartchat_conversa_reservas", joinColumns = @JoinColumn(name = "conversa_id"))
    @Column(name = "reserva_id")
    private Set<Long> reservaIds = new LinkedHashSet<>();

    public Long getId() { return id; }
    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }
    public Usuario getGestor() { return gestor; }
    public void setGestor(Usuario gestor) { this.gestor = gestor; }
    public Imovel getImovel() { return imovel; }
    public void setImovel(Imovel imovel) { this.imovel = imovel; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public void setUltimaMensagemEm(Instant ultimaMensagemEm) { this.ultimaMensagemEm = ultimaMensagemEm; }
    public Set<Long> getReservaIds() { return reservaIds; }

    public boolean participa(Usuario u) {
        return u != null && (cliente.getId().equals(u.getId()) || gestor.getId().equals(u.getId()));
    }

    public Usuario outroLado(Usuario u) {
        return cliente.getId().equals(u.getId()) ? gestor : cliente;
    }
}
