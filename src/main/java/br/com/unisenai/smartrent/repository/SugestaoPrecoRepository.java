package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    Optional<SugestaoPreco> findFirstByImovelIdAndDataReferenciaOrderByDataGeracaoDesc(
            Long imovelId, LocalDate dataReferencia);

    List<SugestaoPreco> findByImovelId(Long imovelId);
}
