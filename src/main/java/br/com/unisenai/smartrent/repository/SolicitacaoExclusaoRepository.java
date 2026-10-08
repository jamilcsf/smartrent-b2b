package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SolicitacaoExclusao;
import br.com.unisenai.smartrent.model.enums.EstadoExclusao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoExclusaoRepository extends JpaRepository<SolicitacaoExclusao, Long> {

    /** A solicitacao em andamento do usuario (no maximo uma, garantido por indice unico parcial). */
    Optional<SolicitacaoExclusao> findFirstByUsuarioIdAndEstadoIn(Long usuarioId, Collection<EstadoExclusao> estados);

    /** Consulta leve usada em toda checagem de restricao. */
    boolean existsByUsuarioIdAndEstadoIn(Long usuarioId, Collection<EstadoExclusao> estados);

    /** Trava a linha: dois cliques no link "nao fui eu" (ou link e botao) nao cancelam duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitacaoExclusao s where s.tokenCancelamentoHash = :hash")
    Optional<SolicitacaoExclusao> findPorHashParaAtualizar(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitacaoExclusao s where s.id = :id")
    Optional<SolicitacaoExclusao> findByIdParaAtualizar(@Param("id") Long id);

    List<SolicitacaoExclusao> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    long countByEstadoIn(Collection<EstadoExclusao> estados);
}
