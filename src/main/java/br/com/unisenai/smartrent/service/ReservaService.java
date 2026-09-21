package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;

    public ReservaService(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    /**
     * Grava a reserva apos checar choque de datas.
     *
     * <p>A checagem e a gravacao correm na mesma transacao; o travamento
     * otimista da entidade (campo versao) cobre a janela entre elas.
     */
    @Transactional
    public Reserva cadastrarReserva(Reserva reserva) {
        boolean conflito = reservaRepository.existeConflitoDeDatas(
                reserva.getImovel().getId(),
                reserva.getDataCheckin(),
                reserva.getDataCheckout());

        if (conflito) {
            throw new IllegalArgumentException("Conflito de datas detectado para este imovel.");
        }

        return reservaRepository.save(reserva);
    }
}
