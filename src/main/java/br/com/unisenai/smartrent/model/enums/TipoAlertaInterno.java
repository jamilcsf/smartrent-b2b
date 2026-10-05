package br.com.unisenai.smartrent.model.enums;

/** Tipos de alerta interno de comportamento no SmartChat. */
public enum TipoAlertaInterno {
    /** Mesmo texto enviado para varias conversas em pouco tempo. */
    ENVIO_EM_MASSA,
    /** Mensagens que citam pagamento ou contato fora da plataforma. */
    SUSPEITA_FRAUDE
}
