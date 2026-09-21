package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.imovelId = :imovelId AND " +
           "(:checkin < r.dataCheckout AND :checkout > r.dataCheckin)")
    boolean existeConflitoDeDatas(Long imovelId, LocalDate checkin, LocalDate checkout);
}
