package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.dto.CalendarioDtos.ImovelResumo;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    /**
     * Interlocutor da conversa: so o nome (e iniciais para o avatar), o papel e {@code verificado}: o
     * e-mail da conta esta verificado (selo "E-mail verificado"; NAO e verificacao de identidade, nenhum documento e
     * conferido). O campo nao se chama "email" de proposito: o endereco nunca sai daqui.
     */
    public record Interlocutor(String nome, String iniciais, String papel, String fotoUrl, boolean verificado) {
    }

    /** Sem id: o chat nao precisa dele e o id sequencial da reserva nao deve sair por aqui. */
    public record ReservaResumo(
            StatusReserva status,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckin,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataCheckout,
            int numeroHospedes) {
    }

    /** {@code codigo} e o UUID publico da conversa: o id sequencial nao sai da API (nem o de usuarios). */
    public record Conversa(
            java.util.UUID codigo,
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
     * marcadores de borrao); nao existe campo para o original. {@code suspeitaFraude} so e verdadeiro para o
     * DESTINATARIO (o autor nunca ve o alerta): a mensagem cita pagamento ou contato fora da plataforma.
     */
    public record Mensagem(
            java.util.UUID codigo,
            String tipo,
            boolean minha,
            String autor,
            String texto,
            int ocorrencias,
            Instant criadaEm,
            boolean lida,
            boolean suspeitaFraude) {
    }

    /** O limite de negocio (SMARTCHAT_MAX_CARACTERES) e do servico; este e so um teto contra corpo gigante. */
    public record EnvioPedido(
            @NotBlank(message = "Escreva uma mensagem.")
            @Size(max = 4000, message = "A mensagem é longa demais.") String texto) {
    }

    public record EnvioResposta(Mensagem mensagem, String aviso) {
    }

    public record Perfil(Interlocutor interlocutor, ImovelResumo imovel, ReservaResumo reserva) {
    }

    public record DenunciaPedido(
            @NotBlank(message = "Escolha o motivo da denúncia.") @Size(max = 40) String motivo,
            @Size(max = 4000, message = "A descrição é longa demais.") String descricao,
            @Size(max = 50, message = "Selecione no máximo 50 mensagens.") List<java.util.UUID> mensagensIds) {
    }

    public record Confirmacao(String mensagem) {
    }

    public record NaoLidas(long total) {
    }

    public record Ticket(String ticket) {
    }
}
