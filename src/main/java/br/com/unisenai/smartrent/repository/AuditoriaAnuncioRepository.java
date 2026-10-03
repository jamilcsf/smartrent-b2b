package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AuditoriaAnuncio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.AuditoriaAnuncio;
import java.util.List;

@Repository
public interface AuditoriaAnuncioRepository extends JpaRepository<AuditoriaAnuncio, Long> {

    List<AuditoriaAnuncio> findByImovelIdOrderByDataHoraDesc(Long imovelId);
}
