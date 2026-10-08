package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AcaoModeracao;
import br.com.unisenai.smartrent.model.enums.TipoAcaoModeracao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcaoModeracaoRepository extends JpaRepository<AcaoModeracao, Long> {

    Page<AcaoModeracao> findAllByOrderByIdDesc(Pageable pagina);

    Page<AcaoModeracao> findByUsuarioIdOrderByIdDesc(Long usuarioId, Pageable pagina);

    List<AcaoModeracao> findTop20ByUsuarioIdOrderByIdDesc(Long usuarioId);

    Optional<AcaoModeracao> findFirstByUsuarioIdAndTipoOrderByIdDesc(Long usuarioId, TipoAcaoModeracao tipo);

    /** Decisao (a mais recente) sobre uma denuncia: procedente ou improcedente. */
    Optional<AcaoModeracao> findFirstByDenunciaIdAndTipoInOrderByIdDesc(Long denunciaId, Collection<TipoAcaoModeracao> tipos);

    Optional<AcaoModeracao> findFirstByAlertaIdAndTipoInOrderByIdDesc(Long alertaId, Collection<TipoAcaoModeracao> tipos);
}
