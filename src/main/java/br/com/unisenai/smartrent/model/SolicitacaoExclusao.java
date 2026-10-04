package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Pedido de exclusao de dados. NADA e excluido automaticamente: o pedido so entra em analise
 * da equipe (antifraude) e, enquanto esta em andamento, a conta sofre restricoes temporarias
 * parciais (AccountRestrictionService). O e-mail do pedido e uma copia (a conta pode mudar de
 * e-mail depois). O token do link "nao fui eu" so existe aqui como hash.
 */
@Entity
@Table(name = "solicitacoes_exclusao")
public class SolicitacaoExclusao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "email_no_pedido", nullable = false, updatable = false, length = 150)
    private String emailNoPedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoExclusao estado = EstadoExclusao.PENDENTE;

    @Column(name = "motivo", length = 500)
    private String motivo;

    /** Sinais de risco para a equipe (contagens e sim/nao; nenhum dado pessoal). */
    @Column(name = "sinais_risco", length = 2000)
    private String sinaisRisco;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    /** Periodo minimo de analise: a equipe so pode aprovar depois deste instante. */
    @Column(name = "analise_apos", nullable = false, updatable = false)
    private Instant analiseApos;

    @Column(name = "analista_id")
    private Long analistaId;

    @Column(name = "decidida_em")
    private Instant decididaEm;

    @Column(name = "observacao_interna", length = 1000)
    private String observacaoInterna;

    @Column(name = "token_cancelamento_hash", nullable = false, updatable = false, length = 64)
    private String tokenCancelamentoHash;

    public SolicitacaoExclusao() {
    }

    public SolicitacaoExclusao(Long usuarioId, String emailNoPedido, String motivo, String sinaisRisco, String ip,
                               Instant criadaEm, Instant analiseApos, String tokenCancelamentoHash) {
        this.usuarioId = usuarioId;
        this.emailNoPedido = emailNoPedido;
        this.motivo = motivo;
        this.sinaisRisco = sinaisRisco;
        this.ip = ip;
        this.criadaEm = criadaEm;
        this.analiseApos = analiseApos;
        this.tokenCancelamentoHash = tokenCancelamentoHash;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEmailNoPedido() {
        return emailNoPedido;
    }

    public EstadoExclusao getEstado() {
        return estado;
    }

    public void setEstado(EstadoExclusao estado) {
        this.estado = estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getSinaisRisco() {
        return sinaisRisco;
    }

    public String getIp() {
        return ip;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getAnaliseApos() {
        return analiseApos;
    }

    public Long getAnalistaId() {
        return analistaId;
    }

    public void setAnalistaId(Long analistaId) {
        this.analistaId = analistaId;
    }

    public Instant getDecididaEm() {
        return decididaEm;
    }

    public void setDecididaEm(Instant decididaEm) {
        this.decididaEm = decididaEm;
    }

    public String getObservacaoInterna() {
        return observacaoInterna;
    }

    public void setObservacaoInterna(String observacaoInterna) {
        this.observacaoInterna = observacaoInterna == null || observacaoInterna.length() <= 1000
                ? observacaoInterna : observacaoInterna.substring(0, 1000);
    }

    public String getTokenCancelamentoHash() {
        return tokenCancelamentoHash;
    }
}
