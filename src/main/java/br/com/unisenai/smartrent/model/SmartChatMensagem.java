package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.TipoMensagem;
import br.com.unisenai.smartrent.security.TextoCifradoConverter;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Mensagem do SmartChat. {@code textoFiltrado} e o unico texto que sai do
 * servidor. {@code textoOriginal} e restrito ao backend (futura moderacao): nao
 * ha getter em nenhum DTO e nenhum endpoint de usuario o devolve. Os dois textos sao cifrados em repouso
 * (AES-256-GCM, {@link TextoCifradoConverter}): nao filtrar nem ordenar por eles em query.
 */
@Entity
@Table(name = "smartchat_mensagens")
public class SmartChatMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador que sai pela API (cursor, denuncia): o id sequencial nao e exposto. */
    @Column(name = "codigo_publico", nullable = false, unique = true, updatable = false)
    private java.util.UUID codigoPublico = java.util.UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private SmartChatConversa conversa;

    /** Nulo nas mensagens de sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMensagem tipo = TipoMensagem.NORMAL;

    @Convert(converter = TextoCifradoConverter.class)
    @Column(name = "texto_filtrado", nullable = false, columnDefinition = "TEXT")
    private String textoFiltrado;

    /** RESTRITO: nunca expor por endpoint de usuario, notificacao ou pre-visualizacao. */
    @Convert(converter = TextoCifradoConverter.class)
    @Column(name = "texto_original", columnDefinition = "TEXT")
    private String textoOriginal;

    /** Categorias detectadas, separadas por virgula (metricas). */
    @Column(name = "categorias", length = 200)
    private String categorias;

    /** HMAC-SHA-256 do texto normalizado (deteccao de envio em massa). Nunca o texto. Nulo nas mensagens antigas e nas de sistema. */
    @Column(name = "texto_hmac", length = 64)
    private String textoHmac;

    @Column(name = "ocorrencias", nullable = false)
    private int ocorrencias;

    @Column(name = "chave_idempotencia", length = 100, unique = true)
    private String chaveIdempotencia;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "lida_em")
    private Instant lidaEm;

    public java.util.UUID getCodigoPublico() { return codigoPublico; }

    public Long getId() { return id; }
    public SmartChatConversa getConversa() { return conversa; }
    public void setConversa(SmartChatConversa conversa) { this.conversa = conversa; }
    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }
    public TipoMensagem getTipo() { return tipo; }
    public void setTipo(TipoMensagem tipo) { this.tipo = tipo; }
    public String getTextoFiltrado() { return textoFiltrado; }
    public void setTextoFiltrado(String textoFiltrado) { this.textoFiltrado = textoFiltrado; }
    public String getTextoOriginal() { return textoOriginal; }
    public void setTextoOriginal(String textoOriginal) { this.textoOriginal = textoOriginal; }
    public String getCategorias() { return categorias; }
    public void setCategorias(String categorias) { this.categorias = categorias; }
    public String getTextoHmac() { return textoHmac; }
    public void setTextoHmac(String textoHmac) { this.textoHmac = textoHmac; }
    public int getOcorrencias() { return ocorrencias; }
    public void setOcorrencias(int ocorrencias) { this.ocorrencias = ocorrencias; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public void setChaveIdempotencia(String chave) { this.chaveIdempotencia = chave; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
    public Instant getLidaEm() { return lidaEm; }
    public void setLidaEm(Instant lidaEm) { this.lidaEm = lidaEm; }
}
