package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import jakarta.persistence.*;

import java.time.Instant;

/** Historico de mudancas de estado de uma solicitacao de exclusao (quem, quando, de qual para qual). */
@Entity
@Table(name = "solicitacao_exclusao_historico")
public class SolicitacaoExclusaoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitacao_id", nullable = false, updatable = false)
    private Long solicitacaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 30)
    private EstadoExclusao estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_novo", nullable = false, length = 30)
    private EstadoExclusao estadoNovo;

    @Column(name = "ocorrida_em", nullable = false, updatable = false)
    private Instant ocorridaEm;

    /** Nulo quando a mudanca e do proprio usuario ou do sistema. */
    @Column(name = "autor_id")
    private Long autorId;

    @Column(name = "observacao", length = 500)
    private String observacao;

    public SolicitacaoExclusaoHistorico() {
    }

    public SolicitacaoExclusaoHistorico(Long solicitacaoId, EstadoExclusao anterior, EstadoExclusao novo,
                                        Instant ocorridaEm, Long autorId, String observacao) {
        this.solicitacaoId = solicitacaoId;
        this.estadoAnterior = anterior;
        this.estadoNovo = novo;
        this.ocorridaEm = ocorridaEm;
        this.autorId = autorId;
        this.observacao = observacao == null || observacao.length() <= 500 ? observacao : observacao.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Long getSolicitacaoId() {
        return solicitacaoId;
    }

    public EstadoExclusao getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoExclusao getEstadoNovo() {
        return estadoNovo;
    }

    public Instant getOcorridaEm() {
        return ocorridaEm;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getObservacao() {
        return observacao;
    }
}
