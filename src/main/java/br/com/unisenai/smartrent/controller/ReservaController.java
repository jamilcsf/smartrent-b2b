package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    @Autowired
    private ReservaRepository reservaRepository;

    // 1. READ ALL - Listar todas as reservas
    @GetMapping
    public List<Reserva> listarTodas() {
        return reservaRepository.findAll();
    }

    // 2. READ BY ID - Buscar uma reserva especifica
    @GetMapping("/{id}")
    public ResponseEntity<Reserva> buscarPorId(@PathVariable Long id) {
        return reservaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. CREATE - Cadastrar nova reserva
    @PostMapping
    public ResponseEntity<Reserva> criarReserva(@RequestBody Reserva reserva) {
        if (reserva.getStatus() == null) {
            reserva.setStatus("CONFIRMADA");
        }
        Reserva nova = reservaRepository.save(reserva);
        return ResponseEntity.ok(nova);
    }

    // 4. UPDATE - Atualizar dados da reserva
    @PutMapping("/{id}")
    public ResponseEntity<Reserva> atualizarReserva(@PathVariable Long id, @RequestBody Reserva dados) {
        return reservaRepository.findById(id).map(reserva -> {
            reserva.setDataCheckin(dados.getDataCheckin());
            reserva.setDataCheckout(dados.getDataCheckout());
            reserva.setValorDiaria(dados.getValorDiaria());
            reserva.setStatus(dados.getStatus());
            Reserva atualizada = reservaRepository.save(reserva);
            return ResponseEntity.ok(atualizada);
        }).orElse(ResponseEntity.notFound().build());
    }

    // 5. DELETE - Cancelar/Excluir reserva
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarReserva(@PathVariable Long id) {
        if (reservaRepository.existsById(id)) {
            reservaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}