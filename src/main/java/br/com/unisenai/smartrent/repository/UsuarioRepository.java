package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.dto.AdminDtos.DiaTotal;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    // ---- Painel de admin (somente leitura) ----

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ContagemRotulo(str(u.papel), count(u)) from Usuario u group by u.papel")
    List<ContagemRotulo> contagemPorPapel();

    long countByAtivoFalse();

    Page<Usuario> findByAtivoFalseOrderByIdDesc(Pageable pagina);

    long countByDataCriacaoGreaterThanEqual(LocalDateTime desde);

    @Query("select new br.com.unisenai.smartrent.dto.AdminDtos$DiaTotal(cast(u.dataCriacao as LocalDate), count(u)) from Usuario u "
            + "where u.dataCriacao >= :desde group by cast(u.dataCriacao as LocalDate) order by cast(u.dataCriacao as LocalDate)")
    List<DiaTotal> cadastrosPorDia(@Param("desde") LocalDateTime desde);
}
