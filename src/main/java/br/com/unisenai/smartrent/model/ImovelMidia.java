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

    @Column(name = "arquivo", nullable = false, length = 120)
    private String arquivo;

    @Column(name = "miniatura", length = 120)
    private String miniatura;

    @Column(name = "mime", nullable = false, length = 40)
    private String mime;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

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
}
