package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.ImovelMidia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.ImovelMidia;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImovelMidiaRepository extends JpaRepository<ImovelMidia, Long> {

    List<ImovelMidia> findByImovelIdOrderByOrdemAscIdAsc(Long imovelId);

    List<ImovelMidia> findByImovelIdInOrderByOrdemAscIdAsc(Collection<Long> imovelIds);

    Optional<ImovelMidia> findByChave(String chave);

    /** Videos prontos para a proxima tentativa de processamento (fila simples em banco). */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM ImovelMidia m WHERE m.statusProcessamento = "
            + "br.com.unisenai.smartrent.model.enums.StatusVideo.PROCESSANDO "
            + "AND (m.proximaTentativaEm IS NULL OR m.proximaTentativaEm <= :agora) ORDER BY m.id")
    List<ImovelMidia> findParaProcessar(@org.springframework.data.repository.query.Param("agora") java.time.Instant agora,
                                        org.springframework.data.domain.Pageable pagina);

    /** Imagens ainda nao reprocessadas sem metadados (job de saneamento), em ordem de id. */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM ImovelMidia m WHERE m.metadadosRemovidos = false "
            + "AND m.tipo IN (br.com.unisenai.smartrent.model.enums.TipoMidia.FOTO, "
            + "br.com.unisenai.smartrent.model.enums.TipoMidia.FOTO_360) AND m.id > :depoisDe ORDER BY m.id")
    List<ImovelMidia> findImagensParaSanear(@org.springframework.data.repository.query.Param("depoisDe") Long depoisDe,
                                            org.springframework.data.domain.Pageable pagina);

    /** Envios iniciados e nunca concluidos (orfaos) sem atividade desde o limite. */
    List<ImovelMidia> findByStatusProcessamentoAndAtualizadoEmBefore(
            br.com.unisenai.smartrent.model.enums.StatusVideo status, java.time.Instant limite);
}
