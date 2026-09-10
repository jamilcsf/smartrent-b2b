package com.smartrentb2b.repository;

import com.smartrentb2b.domain.model.Imovel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImovelRepository extends JpaRepository<Imovel, Long> {

    /** Lista todos os imóveis (ativos e inativos) de um anfitrião. */
    List<Imovel> findByUsuarioId(Long usuarioId);

    /** Lista apenas os imóveis ativos de um anfitrião (uso típico no dashboard). */
    List<Imovel> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    List<Imovel> findByAtivoTrue();
}