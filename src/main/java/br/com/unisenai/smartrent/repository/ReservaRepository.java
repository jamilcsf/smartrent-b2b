package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * Sobreposicao de periodos: existe conflito quando o check-in proposto e
     * anterior ao check-out de uma reserva existente e o check-out proposto e
     * posterior ao check-in dela. Reservas canceladas nao bloqueiam a data.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.imovel.id = :imovelId "
         + "AND r.status <> br.com.unisenai.smartrent.model.enums.StatusReserva.CANCELADA "
         + "AND (:checkin < r.dataCheckout AND :checkout > r.dataCheckin)")
    boolean existeConflitoDeDatas(@Param("imovelId") Long imovelId,
                                  @Param("checkin") LocalDate checkin,
                                  @Param("checkout") LocalDate checkout);

    List<Reserva> findByImovelId(Long imovelId);
}
