package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.dto.CalendarioDtos.ImovelResumo;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Contratos do SmartChat. Nenhum deles carrega o texto original das mensagens,
 * e-mail, telefone ou outro contato do interlocutor.
 */
public final class SmartChatDtos {

    private SmartChatDtos() {
    }

    /** Interlocutor da conversa: so o nome (e iniciais para o avatar) e o papel. */
    public record Interlocutor(String nome, String iniciais, String papel, String fotoUrl) {
    }

    public record ReservaResumo(
            Long id,
            StatusReserva status,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            int numeroHospedes) {
    }

    public record Conversa(
            Long id,
            ImovelResumo imovel,
            Interlocutor interlocutor,
            String ultimaMensagem,
            Instant ultimaMensagemEm,
            long naoLidas,
            ReservaResumo reserva,
            boolean anuncioPublicado,
            String meuPapel) {
    }

    /**
     * Mensagem entregue ao navegador. {@code texto} e o texto JA FILTRADO (com os
     * marcadores de borrao); nao existe campo para o original.
     */
    public record Mensagem(
            Long id,
            String tipo,
            boolean minha,
            String autor,
            String texto,
            int ocorrencias,
            Instant criadaEm,
            boolean lida) {
    }

    public record EnvioPedido(String texto) {
    }

    public record EnvioResposta(Mensagem mensagem, String aviso) {
    }

    public record Perfil(Interlocutor interlocutor, ImovelResumo imovel, ReservaResumo reserva) {
    }

    public record DenunciaPedido(String motivo, String descricao, List<Long> mensagensIds) {
    }

    public record Confirmacao(String mensagem) {
    }

    public record NaoLidas(long total) {
    }

    public record Ticket(String ticket) {
    }
}
