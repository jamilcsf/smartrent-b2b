package com.smartrentb2b.repository;

import com.smartrentb2b.domain.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {

    List<SugestaoPreco> findByImovelIdOrderByDataGeracaoDesc(Long imovelId);

    /** Última sugestão gerada para uma data de referência específica (cache de curto prazo). */
    Optional<SugestaoPreco> findFirstByImovelIdAndDataReferenciaOrderByDataGeracaoDesc(
            Long imovelId, LocalDate dataReferencia);

    /** Usado pelo Dashboard (Chart.js) para plotar a curva de preços sugeridos no período. */
    List<SugestaoPreco> findByImovelIdAndDataReferenciaBetweenOrderByDataReferenciaAsc(
            Long imovelId, LocalDate inicio, LocalDate fim);
}