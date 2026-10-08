package br.com.unisenai.smartrent.model.enums;

/** Tipos de evento de interface coletados pela telemetria de uso (ADR-009). */
public enum TipoEventoUso {
    /** Abertura de uma pagina. */
    VISUALIZACAO,
    /** Clique ou toque, com a posicao na pagina. */
    CLIQUE,
    /** Profundidade maxima de rolagem alcancada (em %), enviada ao sair da pagina. */
    ROLAGEM,
    /** Tempo ativo na pagina (em segundos), enviado ao sair. */
    PERMANENCIA
}
