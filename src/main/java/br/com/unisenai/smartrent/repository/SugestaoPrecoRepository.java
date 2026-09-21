package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SugestaoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SugestaoPrecoRepository extends JpaRepository<SugestaoPreco, Long> {
}
