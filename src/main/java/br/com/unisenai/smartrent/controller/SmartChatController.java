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

    @GetMapping("/conversas/{id}")
    public Conversa buscar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return service.buscar(usuario, id);
    }

    @GetMapping("/conversas/{id}/mensagens")
    public List<Mensagem> mensagens(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
                                    @RequestParam(required = false) Long depoisDe) {
        return service.listarMensagens(usuario, id, depoisDe);
    }

    @PostMapping("/conversas/{id}/mensagens")
    public EnvioResposta enviar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id, @RequestBody EnvioPedido pedido) {
        return service.enviar(usuario, id, pedido == null ? null : pedido.texto());
    }

    @PostMapping("/conversas/{id}/lidas")
    public void marcarLidas(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        service.marcarLidas(usuario, id);
    }

    @GetMapping("/nao-lidas")
    public NaoLidas naoLidas(@AuthenticationPrincipal Usuario usuario) {
        return new NaoLidas(service.naoLidas(usuario));
    }

    @GetMapping("/conversas/{id}/perfil")
    public Perfil perfil(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return service.perfil(usuario, id);
    }

    @PostMapping("/conversas/{id}/denuncias")
    public Confirmacao denunciar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id, @RequestBody DenunciaPedido pedido) {
        return service.denunciar(usuario, id, pedido);
    }

    @PostMapping("/conversas/{id}/bloqueio")
    public Confirmacao bloquear(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
        return service.bloquear(usuario, id);
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
