package br.com.unisenai.smartrent.model.enums;

/** NORMAL: escrita por cliente ou gestor (passa pelo filtro). SISTEMA: gerada pela plataforma (isenta do filtro, imutavel). */
public enum TipoMensagem {
    NORMAL,
    SISTEMA
}
