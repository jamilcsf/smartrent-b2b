package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Reserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Estados de cancelamento: a reserva cancelada libera as datas na hora. */
    String ATIVA = "r.status NOT IN (br.com.unisenai.smartrent.model.enums.StatusReserva.CANCELADA_COM_REEMBOLSO, "
            + "br.com.unisenai.smartrent.model.enums.StatusReserva.CANCELADA_SEM_REEMBOLSO, "
            + "br.com.unisenai.smartrent.model.enums.StatusReserva.CANCELADA_PELO_GESTOR)";

    /**
     * Sobreposicao de periodos: existe conflito quando o check-in proposto e
     * anterior ao check-out de uma reserva existente e o check-out proposto e
     * posterior ao check-in dela. Reservas canceladas nao bloqueiam a data.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.imovel.id = :imovelId AND " + ATIVA
         + " AND (:checkin < r.dataCheckout AND :checkout > r.dataCheckin)")
    boolean existeConflitoDeDatas(@Param("imovelId") Long imovelId,
                                  @Param("checkin") LocalDate checkin,
                                  @Param("checkout") LocalDate checkout);

    /** Mesma regra de sobreposicao, ignorando a propria reserva (usada ao alterar datas). */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.imovel.id = :imovelId AND r.id <> :reservaId AND " + ATIVA
         + " AND (:checkin < r.dataCheckout AND :checkout > r.dataCheckin)")
    boolean existeConflitoDeDatasExceto(@Param("imovelId") Long imovelId,
                                        @Param("reservaId") Long reservaId,
                                        @Param("checkin") LocalDate checkin,
                                        @Param("checkout") LocalDate checkout);

    /** Reservas ativas (nao canceladas) que tocam o periodo [de, ate), para o calendario do gestor. */
    @Query("SELECT r FROM Reserva r WHERE r.imovel.id = :imovelId AND " + ATIVA
         + " AND r.dataCheckin < :ate AND r.dataCheckout > :de ORDER BY r.dataCheckin")
    List<Reserva> findAtivasNoPeriodo(@Param("imovelId") Long imovelId,
                                      @Param("de") LocalDate de,
                                      @Param("ate") LocalDate ate);

    /** Reservas ativas do imovel que tocam o periodo, com status, para decidir sobre bloqueios. */
    @Query("SELECT r FROM Reserva r WHERE r.imovel.id = :imovelId AND " + ATIVA
         + " AND r.dataCheckin < :ate AND r.dataCheckout > :de")
    List<Reserva> findAtivasQueTocam(@Param("imovelId") Long imovelId,
                                     @Param("de") LocalDate de,
                                     @Param("ate") LocalDate ate);

    /** Trava a linha: serializa pagamento, cancelamento e reembolso da mesma reserva (clique duplo, retry). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.id = :id")
    Optional<Reserva> findByIdParaAtualizar(@Param("id") Long id);

    /** Reservas autoatendidas ainda pendentes e criadas antes do limite (nao pagas a tempo). */
    @Query("SELECT r FROM Reserva r WHERE r.status = br.com.unisenai.smartrent.model.enums.StatusReserva.PENDENTE "
         + "AND r.cliente IS NOT NULL AND r.dataCriacao < :limite")
    List<Reserva> findPendentesExpiradas(@Param("limite") LocalDateTime limite);

    List<Reserva> findByImovelId(Long imovelId);

    List<Reserva> findByImovelUsuarioIdOrderByDataCheckinDesc(Long usuarioId);

    List<Reserva> findByClienteIdOrderByDataCheckinDesc(Long clienteId);
}
