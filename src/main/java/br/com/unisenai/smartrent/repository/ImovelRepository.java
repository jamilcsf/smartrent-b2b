package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Imovel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImovelRepository extends JpaRepository<Imovel, Long> {

    /*
     * As comodidades sao uma colecao LAZY e a aplicacao roda com
     * open-in-view desligado: sem o grafo abaixo, le-las fora da transacao
     * lancaria LazyInitializationException. Trazer no mesmo select tambem
     * evita uma consulta por imovel ao montar o catalogo.
     */

    @Override
    @EntityGraph(attributePaths = "comodidades")
    Optional<Imovel> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "comodidades")
    List<Imovel> findAll();

    @EntityGraph(attributePaths = "comodidades")
    List<Imovel> findByAtivoTrue();

    List<Imovel> findByUsuarioId(Long usuarioId);
}
