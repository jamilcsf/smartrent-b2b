package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.BloqueioUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface BloqueioUsuarioRepository extends JpaRepository<BloqueioUsuario, Long> {

    boolean existsByBloqueadorIdAndBloqueadoIdAndCriadoEmAfter(Long bloqueadorId, Long bloqueadoId, Instant depois);
}
