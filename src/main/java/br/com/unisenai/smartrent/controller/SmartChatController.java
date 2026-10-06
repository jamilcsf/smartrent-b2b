package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.SmartChatDtos.Confirmacao;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Conversa;
import br.com.unisenai.smartrent.dto.SmartChatDtos.DenunciaPedido;
import br.com.unisenai.smartrent.dto.SmartChatDtos.EnvioPedido;
import br.com.unisenai.smartrent.dto.SmartChatDtos.EnvioResposta;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Mensagem;
import br.com.unisenai.smartrent.dto.SmartChatDtos.NaoLidas;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Perfil;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Ticket;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ChatEventos;
import br.com.unisenai.smartrent.service.SmartChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * SmartChat. Exige login (SecurityConfig); quem participa de cada conversa e
 * conferido no servico. Nenhuma resposta traz o texto original das mensagens
 * nem e-mail ou telefone do interlocutor.
 */
@RestController
@RequestMapping("/api/smartchat")
public class SmartChatController {

    private final SmartChatService service;
    private final ChatEventos eventos;

    public SmartChatController(SmartChatService service, ChatEventos eventos) {
        this.service = service;
        this.eventos = eventos;
    }

    @GetMapping("/conversas")
    public List<Conversa> listar(@AuthenticationPrincipal Usuario usuario, @RequestParam(required = false) Long imovelId) {
        return service.listar(usuario, imovelId);
    }

    @PostMapping("/conversas/por-imovel/{imovelId}")
    public Conversa abrirPorImovel(@AuthenticationPrincipal Usuario usuario, @PathVariable Long imovelId) {
        return service.abrirPorImovel(usuario, imovelId);
    }

    @PostMapping("/conversas/por-reserva/{reservaId}")
    public Conversa abrirPorReserva(@AuthenticationPrincipal Usuario usuario, @PathVariable Long reservaId) {
        return service.abrirPorReserva(usuario, reservaId);
    }

    @GetMapping("/conversas/{codigo}")
    public Conversa buscar(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo) {
        return service.buscar(usuario, uuid(codigo));
    }

    @GetMapping("/conversas/{codigo}/mensagens")
    public List<Mensagem> mensagens(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo,
                                    @RequestParam(required = false) String depoisDe) {
        return service.listarMensagens(usuario, uuid(codigo), cursor(depoisDe));
    }

    @PostMapping("/conversas/{codigo}/mensagens")
    public EnvioResposta enviar(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo, @Valid @RequestBody EnvioPedido pedido) {
        return service.enviar(usuario, uuid(codigo), pedido.texto());
    }

    @PostMapping("/conversas/{codigo}/lidas")
    public void marcarLidas(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo) {
        service.marcarLidas(usuario, uuid(codigo));
    }

    @GetMapping("/nao-lidas")
    public NaoLidas naoLidas(@AuthenticationPrincipal Usuario usuario) {
        return new NaoLidas(service.naoLidas(usuario));
    }

    @GetMapping("/conversas/{codigo}/perfil")
    public Perfil perfil(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo) {
        return service.perfil(usuario, uuid(codigo));
    }

    @PostMapping("/conversas/{codigo}/denuncias")
    public Confirmacao denunciar(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo, @Valid @RequestBody DenunciaPedido pedido) {
        return service.denunciar(usuario, uuid(codigo), pedido);
    }

    @PostMapping("/conversas/{codigo}/bloqueio")
    public Confirmacao bloquear(@AuthenticationPrincipal Usuario usuario, @PathVariable String codigo) {
        return service.bloquear(usuario, uuid(codigo));
    }

    /** Cursor de paginacao: o codigo publico da ultima mensagem recebida (nulo = desde o inicio). */
    private static java.util.UUID cursor(String depoisDe) {
        if (depoisDe == null || depoisDe.isBlank() || "0".equals(depoisDe)) {
            return null;
        }
        try {
            return java.util.UUID.fromString(depoisDe);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cursor de mensagens invalido.");
        }
    }

    /** UUID mal formado (inclusive um id numerico antigo) recebe o mesmo 404 de conversa inexistente ou de terceiros. */
    private static java.util.UUID uuid(String codigo) {
        try {
            return java.util.UUID.fromString(codigo);
        } catch (IllegalArgumentException e) {
            throw new br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException("Conversa não encontrada.");
        }
    }

    /** Ticket de uso unico (60 s) para abrir o fluxo SSE; EventSource nao envia o cabecalho Authorization. */
    @PostMapping("/stream-ticket")
    public Ticket ticket(@AuthenticationPrincipal Usuario usuario) {
        return new Ticket(eventos.emitirTicket(usuario.getId()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        return eventos.abrir(ticket);
    }
}
