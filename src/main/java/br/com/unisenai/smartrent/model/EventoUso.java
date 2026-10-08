package br.com.unisenai.smartrent.model;

import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Evento de uso da interface, anonimo (sem usuario, IP ou texto de elemento). Imutavel depois de gravado;
 * so a rotina de retencao apaga. Ver ADR-009 e a migration V21.
 */
@Entity
@Table(name = "eventos_uso", indexes = {
        @Index(name = "idx_eventos_uso_pagina", columnList = "pagina, tipo, dia"),
        @Index(name = "idx_eventos_uso_dia", columnList = "dia, tipo"),
        @Index(name = "idx_eventos_uso_ocorrido", columnList = "ocorrido_em")
})
public class EventoUso {

    /** Largura de uma celula da grade do mapa de calor: 20 milesimos da largura (50 colunas). */
    public static final int CELULA_X_PM = 20;
    /** Altura de uma celula da grade do mapa de calor, em pixels. */
    public static final int CELULA_Y_PX = 25;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 12, updatable = false)
    private TipoEventoUso tipo;

    @Column(name = "pagina", nullable = false, length = 80, updatable = false)
    private String pagina;

    @Column(name = "alvo", length = 100, updatable = false)
    private String alvo;

    @Column(name = "sessao_id", nullable = false, length = 36, updatable = false)
    private String sessaoId;

    @Column(name = "papel", nullable = false, length = 12, updatable = false)
    private String papel;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispositivo", nullable = false, length = 8, updatable = false)
    private Dispositivo dispositivo;

    @Column(name = "x_pm", updatable = false)
    private Integer xPm;

    @Column(name = "y_px", updatable = false)
    private Integer yPx;

    @Column(name = "doc_altura", updatable = false)
    private Integer docAltura;

    @Column(name = "x_celula", updatable = false)
    private Integer xCelula;

    @Column(name = "y_celula", updatable = false)
    private Integer yCelula;

    @Column(name = "valor", updatable = false)
    private Integer valor;

    @Column(name = "dia", nullable = false, updatable = false)
    private LocalDate dia;

    @Column(name = "hora", nullable = false, updatable = false)
    private int hora;

    @Column(name = "dia_semana", nullable = false, updatable = false)
    private int diaSemana;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    public EventoUso() {
    }

    public EventoUso(TipoEventoUso tipo, String pagina, String alvo, String sessaoId, String papel,
                     Dispositivo dispositivo, Integer xPm, Integer yPx, Integer docAltura, Integer valor,
                     LocalDate dia, int hora, int diaSemana, Instant ocorridoEm) {
        this.tipo = tipo;
        this.pagina = pagina;
        this.alvo = alvo;
        this.sessaoId = sessaoId;
        this.papel = papel;
        this.dispositivo = dispositivo;
        this.xPm = xPm;
        this.yPx = yPx;
        this.docAltura = docAltura;
        this.xCelula = xPm == null ? null : xPm / CELULA_X_PM;
        this.yCelula = yPx == null ? null : yPx / CELULA_Y_PX;
        this.valor = valor;
        this.dia = dia;
        this.hora = hora;
        this.diaSemana = diaSemana;
        this.ocorridoEm = ocorridoEm;
    }

    public Long getId() { return id; }
    public TipoEventoUso getTipo() { return tipo; }
    public String getPagina() { return pagina; }
    public String getAlvo() { return alvo; }
    public String getSessaoId() { return sessaoId; }
    public String getPapel() { return papel; }
    public Dispositivo getDispositivo() { return dispositivo; }
    public Integer getXPm() { return xPm; }
    public Integer getYPx() { return yPx; }
    public Integer getDocAltura() { return docAltura; }
    public Integer getXCelula() { return xCelula; }
    public Integer getYCelula() { return yCelula; }
    public Integer getValor() { return valor; }
    public LocalDate getDia() { return dia; }
    public int getHora() { return hora; }
    public int getDiaSemana() { return diaSemana; }
    public Instant getOcorridoEm() { return ocorridoEm; }
}
