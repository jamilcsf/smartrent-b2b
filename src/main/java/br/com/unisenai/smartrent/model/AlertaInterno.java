package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.StatusAlertaInterno;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Alerta interno de comportamento (SmartChat) para a equipe analisar. NAO guarda conteudo de mensagem: so o
 * usuario, o tipo, a contagem que o disparou e a janela observada. Nao suspende a conta.
 */
@Entity
@Table(name = "alertas_internos")
public class AlertaInterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoAlertaInterno tipo;

    @Column(name = "contagem", nullable = false)
    private int contagem;

    @Column(name = "janela_inicio", nullable = false)
    private Instant janelaInicio;

    @Column(name = "janela_fim", nullable = false)
    private Instant janelaFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusAlertaInterno status = StatusAlertaInterno.ABERTO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoAlertaInterno getTipo() { return tipo; }
    public void setTipo(TipoAlertaInterno tipo) { this.tipo = tipo; }
    public int getContagem() { return contagem; }
    public void setContagem(int contagem) { this.contagem = contagem; }
    public Instant getJanelaInicio() { return janelaInicio; }
    public void setJanelaInicio(Instant janelaInicio) { this.janelaInicio = janelaInicio; }
    public Instant getJanelaFim() { return janelaFim; }
    public void setJanelaFim(Instant janelaFim) { this.janelaFim = janelaFim; }
    public StatusAlertaInterno getStatus() { return status; }
    public void setStatus(StatusAlertaInterno status) { this.status = status; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
