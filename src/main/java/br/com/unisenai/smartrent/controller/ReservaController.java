package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.dto.ReservaResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.ReservaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(origins = "*")
public class ReservaController {

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final ReservaService reservaService;

    public ReservaController(ReservaRepository reservaRepository,
                             ImovelRepository imovelRepository,
                             ReservaService reservaService) {
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.reservaService = reservaService;
    }

    @GetMapping
    public List<ReservaResponse> listarTodas() {
        return reservaRepository.findAll().stream().map(ReservaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> buscarPorId(@PathVariable Long id) {
        return reservaRepository.findById(id)
                .map(ReservaResponse::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> criarReserva(@RequestBody ReservaRequest req) {
        Optional<Imovel> imovel = imovelRepository.findById(req.imovelId());
        if (imovel.isEmpty()) {
            return ResponseEntity.badRequest().body("Imovel nao encontrado: " + req.imovelId());
        }

        Reserva reserva = new Reserva();
        reserva.setImovel(imovel.get());
        aplicar(req, reserva);
        reserva.setStatus(req.status() == null ? StatusReserva.CONFIRMADA : req.status());
        reserva.setOrigem(req.origem() == null ? OrigemReserva.DIRETA : req.origem());

        try {
            return ResponseEntity.ok(ReservaResponse.de(reservaService.cadastrarReserva(reserva)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservaResponse> atualizarReserva(@PathVariable Long id,
                                                            @RequestBody ReservaRequest req) {
        return reservaRepository.findById(id).map(reserva -> {
            aplicar(req, reserva);
            if (req.status() != null) {
                reserva.setStatus(req.status());
            }
            if (req.origem() != null) {
                reserva.setOrigem(req.origem());
            }
            return ResponseEntity.ok(ReservaResponse.de(reservaRepository.save(reserva)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarReserva(@PathVariable Long id) {
        if (reservaRepository.existsById(id)) {
            reservaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private void aplicar(ReservaRequest req, Reserva reserva) {
        reserva.setHospedeNome(req.hospedeNome());
        reserva.setHospedeEmail(req.hospedeEmail());
        reserva.setHospedeTelefone(req.hospedeTelefone());
        reserva.setDataCheckin(req.dataCheckin());
        reserva.setDataCheckout(req.dataCheckout());
        reserva.setValorTotal(req.valorTotal());
        reserva.setObservacoes(req.observacoes());
    }
}
