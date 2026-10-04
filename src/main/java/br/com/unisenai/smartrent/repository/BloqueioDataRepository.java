package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.BloqueioData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BloqueioDataRepository extends JpaRepository<BloqueioData, Long> {

    /**
     * Bloqueio e reserva se tocam quando o bloqueio comeca antes do check-out e
     * termina em ou depois do check-in (o bloqueio inclui o proprio dia final).
     */
    @Query("SELECT COUNT(b) > 0 FROM BloqueioData b WHERE b.imovel.id = :imovelId "
         + "AND b.dataInicio < :checkout AND b.dataFim >= :checkin")
    boolean existeBloqueio(@Param("imovelId") Long imovelId,
                           @Param("checkin") LocalDate checkin,
                           @Param("checkout") LocalDate checkout);

    /** Bloqueios que tocam as noites [de, ateInclusive]. */
    @Query("SELECT b FROM BloqueioData b WHERE b.imovel.id = :imovelId "
         + "AND b.dataInicio <= :ate AND b.dataFim >= :de ORDER BY b.dataInicio")
    List<BloqueioData> findNoPeriodo(@Param("imovelId") Long imovelId,
                                     @Param("de") LocalDate de,
                                     @Param("ate") LocalDate ate);

    List<BloqueioData> findByImovelIdOrderByDataInicio(Long imovelId);

    /** Bloqueios de todos os imoveis do gestor que tocam [de, ate]; usado nas estatisticas. */
    @Query("SELECT b FROM BloqueioData b WHERE b.imovel.usuario.id = :gestorId "
         + "AND b.dataInicio <= :ate AND b.dataFim >= :de")
    List<BloqueioData> findDoGestorNoPeriodo(@Param("gestorId") Long gestorId,
                                             @Param("de") LocalDate de,
                                             @Param("ate") LocalDate ate);
}
