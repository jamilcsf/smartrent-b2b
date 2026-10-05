package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.VerificacaoEmail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificacaoEmailRepository extends JpaRepository<VerificacaoEmail, Long> {

    /** Links ainda em aberto (nem usados nem cancelados) do usuario; no maximo um. */
    @Query("select v from VerificacaoEmail v where v.usuarioId = :usuarioId and v.usadoEm is null and v.canceladaEm is null")
    List<VerificacaoEmail> abertas(@Param("usuarioId") Long usuarioId);

    /** Busca pelo hash do token travando a linha: dois cliques simultaneos no link nao o usam duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificacaoEmail v where v.tokenHash = :hash")
    Optional<VerificacaoEmail> findPorHashParaAtualizar(@Param("hash") String hash);
}
