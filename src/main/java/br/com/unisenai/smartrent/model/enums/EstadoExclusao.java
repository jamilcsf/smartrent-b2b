package br.com.unisenai.smartrent.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de uma solicitacao de exclusao de dados.
 *
 * <p>{@link #aberta()} (PENDENTE, EM_ANALISE) e o que a equipe ainda pode analisar.
 * {@link #emAndamento()} acrescenta APROVADA (aprovada, exclusao ainda nao executada): enquanto
 * a solicitacao esta em andamento valem as restricoes temporarias e nao se pode abrir outra.
 * As restricoes terminam em CANCELADA_PELO_USUARIO, NEGADA ou CONCLUIDA.
 */
public enum EstadoExclusao {
    PENDENTE,
    EM_ANALISE,
    APROVADA,
    NEGADA,
    CONCLUIDA,
    CANCELADA_PELO_USUARIO;

    private static final Set<EstadoExclusao> ABERTOS = EnumSet.of(PENDENTE, EM_ANALISE);
    private static final Set<EstadoExclusao> EM_ANDAMENTO = EnumSet.of(PENDENTE, EM_ANALISE, APROVADA);

    public boolean aberta() {
        return ABERTOS.contains(this);
    }

    public boolean emAndamento() {
        return EM_ANDAMENTO.contains(this);
    }

    public static Set<EstadoExclusao> estadosEmAndamento() {
        return EnumSet.copyOf(EM_ANDAMENTO);
    }
}
