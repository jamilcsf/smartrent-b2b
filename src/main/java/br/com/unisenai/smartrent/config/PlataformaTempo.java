package br.com.unisenai.smartrent.config;

import java.time.ZoneId;

/**
 * Fuso oficial da plataforma (horario de Brasilia). Ponto unico de leitura
 * para quem nao consegue receber o {@code Clock} por injecao (entidades JPA).
 * O valor vem de {@code smartrent.timezone} (APP_TIMEZONE) e e fixado na subida
 * por {@link ClockConfig}; o nome IANA nunca e substituido por deslocamento fixo.
 */
public final class PlataformaTempo {

    public static final String PADRAO = "America/Sao_Paulo";

    private static volatile ZoneId zona = ZoneId.of(PADRAO);

    private PlataformaTempo() {
    }

    public static ZoneId zona() {
        return zona;
    }

    static void definir(ZoneId nova) {
        zona = nova;
    }
}
