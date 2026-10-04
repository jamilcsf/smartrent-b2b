package br.com.unisenai.smartrent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Lembrete de edicao esquecida. A chave unica (imovel, edicao, numero, canal)
 * torna o envio idempotente: um segundo disparo do job encontra a linha e nao
 * reenvia. {@code enviadoEm} nulo significa "ainda nao entregue", e e o que
 * permite tentar de novo na proxima execucao depois de uma falha.
 */
@Entity
@Table(name = "lembretes_edicao", uniqueConstraints = @UniqueConstraint(name = "uk_lembrete", columnNames = {"imovel_id", "edicao_iniciada_em", "numero", "canal"}))
public class LembreteEdicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lembrete_imovel"))
    private Imovel imovel;

    @Column(name = "edicao_iniciada_em", nullable = false, updatable = false)
    private LocalDateTime edicaoIniciadaEm;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "canal", nullable = false, length = 20, updatable = false)
    private String canal;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "erro", length = 500)
    private String erro;

    public LembreteEdicao() {
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

    public LocalDateTime getEdicaoIniciadaEm() {
        return edicaoIniciadaEm;
    }

    public void setEdicaoIniciadaEm(LocalDateTime edicaoIniciadaEm) {
        this.edicaoIniciadaEm = edicaoIniciadaEm;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public void setEnviadoEm(LocalDateTime enviadoEm) {
        this.enviadoEm = enviadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public void setTentativas(int tentativas) {
        this.tentativas = tentativas;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }
}
