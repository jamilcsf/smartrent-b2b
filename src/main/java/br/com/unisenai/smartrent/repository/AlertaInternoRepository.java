package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AlertaInterno;
import br.com.unisenai.smartrent.model.enums.StatusAlertaInterno;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AlertaInternoRepository extends JpaRepository<AlertaInterno, Long> {

    /** Ja existe alerta deste tipo para o usuario criado depois do instante? (um por usuario e tipo na janela) */
    boolean existsByUsuarioIdAndTipoAndCriadoEmAfter(Long usuarioId, TipoAlertaInterno tipo, Instant depois);

    long countByUsuarioIdAndStatus(Long usuarioId, StatusAlertaInterno status);
}
