package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AceiteTermo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.AceiteTermo;
import java.util.List;

@Repository
public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, Long> {

    List<AceiteTermo> findByImovelIdOrderByDataHoraDesc(Long imovelId);
}
