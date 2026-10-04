package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AuditoriaConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface AuditoriaContaRepository extends JpaRepository<AuditoriaConta, Long> {

    List<AuditoriaConta> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    /** Eventos do usuario desde um instante, de certas acoes (sinais de risco da exclusao). */
    @Query("select a from AuditoriaConta a where a.usuarioId = :usuarioId and a.ocorridaEm >= :desde and a.acao in :acoes")
    List<AuditoriaConta> recentes(@Param("usuarioId") Long usuarioId, @Param("desde") Instant desde,
                                  @Param("acoes") Collection<String> acoes);

    /** Ja houve evento deste usuario, desde o instante, vindo deste IP? */
    @Query("select count(a) > 0 from AuditoriaConta a where a.usuarioId = :usuarioId and a.ip = :ip and a.ocorridaEm >= :desde")
    boolean existeDoIp(@Param("usuarioId") Long usuarioId, @Param("ip") String ip, @Param("desde") Instant desde);
}
