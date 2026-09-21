package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.stereotype.Service;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;

    public ReservaService(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    public Reserva cadastrarReserva(Reserva reserva) {
        boolean conflito = reservaRepository.existeConflitoDeDatas(
            reserva.getImovelId(), 
            reserva.getDataCheckin(), 
            reserva.getDataCheckout()
        );

        if (conflito) {
            throw new IllegalArgumentException("Conflito de datas detectado para este imovel.");
        }

        return reservaRepository.save(reserva);
    }
}
