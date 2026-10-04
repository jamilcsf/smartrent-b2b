package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SolicitacaoExclusaoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitacaoExclusaoHistoricoRepository extends JpaRepository<SolicitacaoExclusaoHistorico, Long> {

    List<SolicitacaoExclusaoHistorico> findBySolicitacaoIdOrderByIdAsc(Long solicitacaoId);
}
