package com.smartrentb2b.repository;

import com.smartrentb2b.domain.model.Reserva;
import com.smartrentb2b.domain.model.enums.StatusReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório de {@link Reserva}. Concentra a trava de conflito de datas
 * (requisito Must Have da matriz MoSCoW): duas reservas do MESMO imóvel
 * conflitam quando seus períodos se sobrepõem — teste clássico de
 * intervalos {@code (inicioA < fimB) AND (fimA > inicioB)}.
 * <p>
 * Reservas com status {@link StatusReserva#CANCELADA} nunca bloqueiam um
 * novo período. O parâmetro {@code reservaIdParaExcluir} permite ignorar a
 * própria reserva ao validar uma edição (evita falso-positivo quando o
 * usuário salva a reserva sem alterar as datas).
 * <p>
 * Uso recomendado no service, antes de qualquer {@code save}:
 * <pre>{@code
 * if (reservaRepository.existeConflitoDeDatas(imovelId, checkin, checkout, reservaIdAtualOuNull)) {
 *     throw new ConflitoDeReservaException(...);
 * }
 * }</pre>
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByImovelIdOrderByDataCheckinAsc(Long imovelId);

    List<Reserva> findByImovelIdAndStatus(Long imovelId, StatusReserva status);

    List<Reserva> findByDataCheckinBetween(LocalDate inicio, LocalDate fim);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Reserva r
            WHERE r.imovel.id = :imovelId
              AND r.status <> :statusIgnorado
              AND (:reservaIdParaExcluir IS NULL OR r.id <> :reservaIdParaExcluir)
              AND r.dataCheckin < :dataCheckout
              AND r.dataCheckout > :dataCheckin
            """)
    boolean existeSobreposicaoDeDatas(@Param("imovelId") Long imovelId,
                                       @Param("dataCheckin") LocalDate dataCheckin,
                                       @Param("dataCheckout") LocalDate dataCheckout,
                                       @Param("reservaIdParaExcluir") Long reservaIdParaExcluir,
                                       @Param("statusIgnorado") StatusReserva statusIgnorado);

    @Query("""
            SELECT r
            FROM Reserva r
            WHERE r.imovel.id = :imovelId
              AND r.status <> :statusIgnorado
              AND (:reservaIdParaExcluir IS NULL OR r.id <> :reservaIdParaExcluir)
              AND r.dataCheckin < :dataCheckout
              AND r.dataCheckout > :dataCheckin
            ORDER BY r.dataCheckin ASC
            """)
    List<Reserva> buscarReservasConflitantes(@Param("imovelId") Long imovelId,
                                              @Param("dataCheckin") LocalDate dataCheckin,
                                              @Param("dataCheckout") LocalDate dataCheckout,
                                              @Param("reservaIdParaExcluir") Long reservaIdParaExcluir,
                                              @Param("statusIgnorado") StatusReserva statusIgnorado);

    /**
     * Atalho de conveniência: aplica a regra padrão do negócio (ignora
     * reservas {@link StatusReserva#CANCELADA}). Use este método no
     * dia a dia; use as variantes acima apenas se precisar customizar o
     * status ignorado.
     */
    default boolean existeConflitoDeDatas(Long imovelId, LocalDate dataCheckin,
                                           LocalDate dataCheckout, Long reservaIdParaExcluir) {
        return existeSobreposicaoDeDatas(imovelId, dataCheckin, dataCheckout,
                reservaIdParaExcluir, StatusReserva.CANCELADA);
    }

    default List<Reserva> buscarConflitos(Long imovelId, LocalDate dataCheckin,
                                           LocalDate dataCheckout, Long reservaIdParaExcluir) {
        return buscarReservasConflitantes(imovelId, dataCheckin, dataCheckout,
                reservaIdParaExcluir, StatusReserva.CANCELADA);
    }
}