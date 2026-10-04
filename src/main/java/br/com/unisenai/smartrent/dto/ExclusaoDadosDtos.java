package br.com.unisenai.smartrent.dto;

import java.time.Instant;
import java.util.List;

/** DTOs da solicitacao de exclusao de dados. Nenhum leva id de usuario: o servidor usa o autenticado. */
public final class ExclusaoDadosDtos {

    private ExclusaoDadosDtos() {
    }

    /** Pedido do usuario: motivo opcional e reautenticacao (senha atual ou credencial Google). */
    public record Pedido(String motivo, String senhaAtual, String credencialGoogle) {
        /** Fixo: nunca imprimir senha nem motivo em log. */
        @Override
        public String toString() {
            return "Pedido[***]";
        }
    }

    /** Link "nao fui eu" do e-mail: token de uso unico. */
    public record CancelamentoPorLink(String token) {
        @Override
        public String toString() {
            return "CancelamentoPorLink[***]";
        }
    }

    /** Solicitacao em andamento do proprio usuario. */
    public record Solicitacao(String estado, Instant criadaEm, boolean podeCancelar) {
    }

    /**
     * Situacao do usuario em relacao ao pedido de exclusao, com os textos (provisorios, de um
     * arquivo unico) do modal e as acoes que ficam temporariamente restritas durante a analise.
     */
    public record Status(Solicitacao solicitacao, String textoModal, List<String> acoesRestritas,
                         List<String> restricoesAtivas, int analiseMinHoras) {
    }
}
