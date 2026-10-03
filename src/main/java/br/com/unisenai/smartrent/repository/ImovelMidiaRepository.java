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
}
