package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.security.TextoCifradoConverter;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Denuncia registrada em uma conversa (protótipo: so persiste, para o futuro
 * modulo de administracao consumir). Nao e visivel ao denunciado.
 */
@Entity
@Table(name = "smartchat_denuncias")
public class DenunciaChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private SmartChatConversa conversa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "denunciante_id", nullable = false)
    private Usuario denunciante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "denunciado_id", nullable = false)
    private Usuario denunciado;

    @Column(name = "motivo", nullable = false, length = 40)
    private String motivo;

    /** Cifrada em repouso (AES-256-GCM); nao usar em filtros de query. */
    @Convert(converter = TextoCifradoConverter.class)
    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    /** Ids das mensagens anexadas, separados por virgula. */
    @Column(name = "mensagens_anexadas", length = 500)
    private String mensagensAnexadas;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDENTE";

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    public Long getId() { return id; }
    public SmartChatConversa getConversa() { return conversa; }
    public void setConversa(SmartChatConversa conversa) { this.conversa = conversa; }
    public Usuario getDenunciante() { return denunciante; }
    public void setDenunciante(Usuario denunciante) { this.denunciante = denunciante; }
    public Usuario getDenunciado() { return denunciado; }
    public void setDenunciado(Usuario denunciado) { this.denunciado = denunciado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getMensagensAnexadas() { return mensagensAnexadas; }
    public void setMensagensAnexadas(String m) { this.mensagensAnexadas = m; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
}
