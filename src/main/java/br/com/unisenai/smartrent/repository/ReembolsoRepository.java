package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {

    Optional<Reembolso> findByReservaId(Long reservaId);

    /** Estornos que ainda precisam de uma tentativa (PENDENTE ou FALHA) e ja passaram da espera. */
    @Query("SELECT r FROM Reembolso r WHERE r.status IN (br.com.unisenai.smartrent.model.enums.StatusReembolso.PENDENTE, "
         + "br.com.unisenai.smartrent.model.enums.StatusReembolso.FALHA) AND r.tentativas < :max "
         + "AND (r.proximaTentativaEm IS NULL OR r.proximaTentativaEm <= :agora)")
    List<Reembolso> findParaTentar(@Param("agora") Instant agora, @Param("max") int max);

    List<Reembolso> findByReservaImovelUsuarioIdAndStatus(Long gestorId, StatusReembolso status);
}
