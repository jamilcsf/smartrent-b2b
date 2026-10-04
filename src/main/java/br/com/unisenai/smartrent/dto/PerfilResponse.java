package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.PapelUsuario;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Perfil do proprio usuario autenticado. Nao carrega id: o servidor sempre usa o
 * usuario da sessao, nunca um identificador vindo do cliente.
 *
 * @param dataCadastro horario de Brasilia (sem fuso, como o restante das datas legadas)
 */
public record PerfilResponse(String nome, String email, PapelUsuario papel, String fotoUrl,
                             LocalDateTime dataCadastro, boolean senhaDefinida,
                             ExclusaoResumo exclusao, List<String> restricoes) {

    /** Solicitacao de exclusao aberta (nulo quando nao ha). */
    public record ExclusaoResumo(String estado, Instant desde) {
    }
}
