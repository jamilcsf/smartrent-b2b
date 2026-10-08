package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Comunicado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComunicadoRepository extends JpaRepository<Comunicado, Long> {

    List<Comunicado> findTop50ByDestinatarioIdOrderByIdDesc(Long destinatarioId);

    long countByDestinatarioIdAndLidoEmIsNull(Long destinatarioId);

    /** Enviados pela administracao (painel). */
    Page<Comunicado> findAllByOrderByIdDesc(Pageable pagina);
}
