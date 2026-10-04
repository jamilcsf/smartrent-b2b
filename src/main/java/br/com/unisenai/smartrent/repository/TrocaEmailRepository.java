package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.TrocaEmail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrocaEmailRepository extends JpaRepository<TrocaEmail, Long> {

    /** Pedidos ainda em aberto (nem usados nem cancelados) do usuario; no maximo um. */
    @Query("select t from TrocaEmail t where t.usuarioId = :usuarioId and t.usadoEm is null and t.canceladaEm is null")
    List<TrocaEmail> abertas(@Param("usuarioId") Long usuarioId);

    /** Busca pelo hash do token travando a linha: dois cliques simultaneos no link nao confirmam duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TrocaEmail t where t.tokenHash = :hash")
    Optional<TrocaEmail> findPorHashParaAtualizar(@Param("hash") String hash);
}
