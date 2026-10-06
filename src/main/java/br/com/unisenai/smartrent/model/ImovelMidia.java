package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Foto, foto 360 ou video de um anuncio. O arquivo vive no {@code MidiaStorage};
 * aqui ficam so os metadados. A {@code chave} (UUID) e o que vai na URL
 * publica: nao e adivinhavel, entao o endereco de uma midia ainda nao
 * publicada nao vaza por enumeracao de ids.
 */
@Entity
@Table(name = "imovel_midias", indexes = @Index(name = "idx_midias_imovel", columnList = "imovel_id"))
public class ImovelMidia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_midia_imovel"))
    private Imovel imovel;

    @Column(name = "chave", nullable = false, unique = true, length = 36, updatable = false)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMidia tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoMidia estado = EstadoMidia.ATIVA;

    @Column(name = "arquivo", nullable = false, length = 160)
    private String arquivo;

    @Column(name = "miniatura", length = 120)
    private String miniatura;

    @Column(name = "mime", nullable = false, length = 40)
    private String mime;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    /** Imagem reprocessada sem metadados (EXIF/GPS). Falso nas fotos antigas ate o job de saneamento. */
    @Column(name = "metadados_removidos", nullable = false)
    private boolean metadadosRemovidos;

    @Column(name = "largura")
    private Integer largura;

    @Column(name = "altura")
    private Integer altura;

    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "capa", nullable = false)
    private boolean capa;

    /** Etapa do video (nulo nas imagens). So PRONTO aparece para clientes. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_processamento", length = 15)
    private br.com.unisenai.smartrent.model.enums.StatusVideo statusProcessamento;

    @Column(name = "motivo_falha", length = 300)
    private String motivoFalha;

    @Column(name = "poster", length = 160)
    private String poster;

    @Column(name = "hls_mestre", length = 160)
    private String hlsMestre;

    @Column(name = "original_arquivo", length = 160)
    private String originalArquivo;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_em")
    private java.time.Instant proximaTentativaEm;

    /** Envio em partes: tamanho esperado e bytes ja recebidos (permite retomar). */
    @Column(name = "tamanho_total")
    private Long tamanhoTotal;

    @Column(name = "bytes_recebidos")
    private Long bytesRecebidos;

    @Column(name = "atualizado_em")
    private java.time.Instant atualizadoEm;

    @Column(name = "data_envio", nullable = false, updatable = false)
    private LocalDateTime dataEnvio;

    public ImovelMidia() {
    }

    @PrePersist
    protected void aoPersistir() {
        if (this.dataEnvio == null) {
            this.dataEnvio = LocalDateTime.now(br.com.unisenai.smartrent.config.PlataformaTempo.zona());
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Imovel getImovel() {
        return imovel;
    }

    public void setImovel(Imovel imovel) {
        this.imovel = imovel;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public TipoMidia getTipo() {
        return tipo;
    }

    public void setTipo(TipoMidia tipo) {
        this.tipo = tipo;
    }

    public EstadoMidia getEstado() {
        return estado;
    }

    public void setEstado(EstadoMidia estado) {
        this.estado = estado;
    }

    public String getArquivo() {
        return arquivo;
    }

    public void setArquivo(String arquivo) {
        this.arquivo = arquivo;
    }

    public String getMiniatura() {
        return miniatura;
    }

    public void setMiniatura(String miniatura) {
        this.miniatura = miniatura;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public boolean isMetadadosRemovidos() { return metadadosRemovidos; }
    public void setMetadadosRemovidos(boolean v) { this.metadadosRemovidos = v; }
    public Integer getLargura() {
        return largura;
    }

    public void setLargura(Integer largura) {
        this.largura = largura;
    }

    public Integer getAltura() {
        return altura;
    }

    public void setAltura(Integer altura) {
        this.altura = altura;
    }

    public Integer getDuracaoSegundos() {
        return duracaoSegundos;
    }

    public void setDuracaoSegundos(Integer duracaoSegundos) {
        this.duracaoSegundos = duracaoSegundos;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isCapa() {
        return capa;
    }

    public void setCapa(boolean capa) {
        this.capa = capa;
    }

    public LocalDateTime getDataEnvio() {
        return dataEnvio;
    }

    public void setDataEnvio(LocalDateTime dataEnvio) {
        this.dataEnvio = dataEnvio;
    }

    public br.com.unisenai.smartrent.model.enums.StatusVideo getStatusProcessamento() { return statusProcessamento; }
    public void setStatusProcessamento(br.com.unisenai.smartrent.model.enums.StatusVideo v) { this.statusProcessamento = v; }
    public String getMotivoFalha() { return motivoFalha; }
    public void setMotivoFalha(String motivoFalha) { this.motivoFalha = motivoFalha; }
    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }
    public String getHlsMestre() { return hlsMestre; }
    public void setHlsMestre(String hlsMestre) { this.hlsMestre = hlsMestre; }
    public String getOriginalArquivo() { return originalArquivo; }
    public void setOriginalArquivo(String originalArquivo) { this.originalArquivo = originalArquivo; }
    public int getTentativas() { return tentativas; }
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
    public java.time.Instant getProximaTentativaEm() { return proximaTentativaEm; }
    public void setProximaTentativaEm(java.time.Instant v) { this.proximaTentativaEm = v; }
    public Long getTamanhoTotal() { return tamanhoTotal; }
    public void setTamanhoTotal(Long tamanhoTotal) { this.tamanhoTotal = tamanhoTotal; }
    public Long getBytesRecebidos() { return bytesRecebidos; }
    public void setBytesRecebidos(Long bytesRecebidos) { this.bytesRecebidos = bytesRecebidos; }
    public java.time.Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(java.time.Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    /** Imagens sempre; video so quando o processamento terminou (PRONTO). */
    public boolean visivelAoPublico() {
        return tipo != null && (tipo.imagem() || statusProcessamento == br.com.unisenai.smartrent.model.enums.StatusVideo.PRONTO);
    }
}
