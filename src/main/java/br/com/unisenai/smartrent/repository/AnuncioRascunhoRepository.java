package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AnuncioRascunho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.AnuncioRascunho;
import java.util.Optional;

@Repository
public interface AnuncioRascunhoRepository extends JpaRepository<AnuncioRascunho, Long> {

    Optional<AnuncioRascunho> findByImovelId(Long imovelId);

    void deleteByImovelId(Long imovelId);
}
