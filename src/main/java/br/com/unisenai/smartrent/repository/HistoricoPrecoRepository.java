package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.HistoricoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.HistoricoPreco;
import java.util.List;

@Repository
public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {

    List<HistoricoPreco> findByImovelIdOrderByDataHoraDesc(Long imovelId);
}
