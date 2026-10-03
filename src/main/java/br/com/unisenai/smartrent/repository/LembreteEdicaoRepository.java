package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.LembreteEdicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.unisenai.smartrent.model.LembreteEdicao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LembreteEdicaoRepository extends JpaRepository<LembreteEdicao, Long> {

    Optional<LembreteEdicao> findByImovelIdAndEdicaoIniciadaEmAndNumeroAndCanal(
            Long imovelId, LocalDateTime edicaoIniciadaEm, int numero, String canal);

    List<LembreteEdicao> findByImovelIdAndEdicaoIniciadaEm(Long imovelId, LocalDateTime edicaoIniciadaEm);
}
