package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ReservaClienteDtos.CancelamentoPedido;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.PagamentoPedido;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Pedido;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Previa;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Resposta;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Simulacao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.CancelamentoService;
import br.com.unisenai.smartrent.service.ReservaClienteMapper;
import br.com.unisenai.smartrent.service.ReservaClienteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Reservas do proprio cliente: so enxerga e altera as suas (conferido nos servicos). */
@RestController
@RequestMapping("/api/cliente/reservas")
public class ReservaClienteController {

    private final ReservaClienteService service;
    private final CancelamentoService cancelamento;
    private final ReservaClienteMapper mapper;

    public ReservaClienteController(ReservaClienteService service, CancelamentoService cancelamento,
                                    ReservaClienteMapper mapper) {
        this.service = service;
        this.cancelamento = cancelamento;
        this.mapper = mapper;
    }

    @GetMapping("/previa")
    public Previa previa(@AuthenticationPrincipal Usuario cliente,
                         @RequestParam Long imovelId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCheckin,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCheckout,
                         @RequestParam(required = false) Integer numeroHospedes) {
        return service.previa(cliente, new Pedido(imovelId, dataCheckin, dataCheckout, numeroHospedes, null, null));
    }

    @PostMapping
    public Resposta criar(@AuthenticationPrincipal Usuario cliente, @RequestBody Pedido pedido) {
        return service.criar(cliente, pedido);
    }

    @GetMapping
    public List<Resposta> listar(@AuthenticationPrincipal Usuario cliente) {
        return service.listar(cliente);
    }

    @GetMapping("/{id}")
    public Resposta buscar(@AuthenticationPrincipal Usuario cliente, @PathVariable Long id) {
        return service.buscar(cliente, id);
    }

    @PostMapping("/{id}/pagar")
    public Resposta pagar(@AuthenticationPrincipal Usuario cliente, @PathVariable Long id,
                          @RequestBody(required = false) PagamentoPedido pedido) {
        return service.pagar(cliente, id, pedido == null ? null : pedido.token());
    }

    /** O servidor calcula e o front so exibe: a decisao final e recalculada ao confirmar. */
    @GetMapping("/{id}/simulacao-cancelamento")
    public Simulacao simular(@AuthenticationPrincipal Usuario cliente, @PathVariable Long id) {
        return cancelamento.simular(cliente, id);
    }

    @PostMapping("/{id}/cancelar")
    public Resposta cancelar(@AuthenticationPrincipal Usuario cliente, @PathVariable Long id,
                             @RequestBody(required = false) CancelamentoPedido pedido) {
        cancelamento.cancelarPeloCliente(cliente, id, pedido == null ? null : pedido.motivo());
        return service.buscar(cliente, id);
    }
}
