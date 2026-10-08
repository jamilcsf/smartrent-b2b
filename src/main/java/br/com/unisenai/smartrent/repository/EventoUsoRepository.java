package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.dto.TelemetriaDtos.AtividadeHora;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.CelulaCalor;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.DiaUso;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ElementoClicado;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.PaginaVista;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.Permanencia;
import br.com.unisenai.smartrent.model.EventoUso;
import br.com.unisenai.smartrent.model.enums.Dispositivo;
import br.com.unisenai.smartrent.model.enums.TipoEventoUso;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Agregacoes da telemetria. Todas filtram por {@code dia >= :desde} (indice) e agregam no banco: o painel nunca
 * carrega eventos brutos na memoria. JPQL portavel: a grade do mapa, o dia e a hora ja vem gravados.
 */
@Repository
public interface EventoUsoRepository extends JpaRepository<EventoUso, Long> {

    @Query("select count(e) from EventoUso e where e.tipo = :tipo and e.dia >= :desde")
    long contar(@Param("tipo") TipoEventoUso tipo, @Param("desde") LocalDate desde);

    @Query("select count(distinct e.sessaoId) from EventoUso e where e.dia >= :desde")
    long contarSessoes(@Param("desde") LocalDate desde);

    @Query("select count(distinct e.sessaoId) from EventoUso e where e.tipo = :tipo and e.pagina like :padrao and e.dia >= :desde")
    long contarSessoesNaPagina(@Param("tipo") TipoEventoUso tipo, @Param("padrao") String padrao,
                               @Param("desde") LocalDate desde);

    @Query("select avg(e.valor) from EventoUso e where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.PERMANENCIA and e.dia >= :desde")
    Double permanenciaMedia(@Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$DiaUso(e.dia, count(e), count(distinct e.sessaoId)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.VISUALIZACAO and e.dia >= :desde "
            + "group by e.dia order by e.dia")
    List<DiaUso> serieDiaria(@Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$PaginaVista(e.pagina, count(e), count(distinct e.sessaoId)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.VISUALIZACAO and e.dia >= :desde "
            + "group by e.pagina order by count(e) desc")
    List<PaginaVista> paginasMaisVistas(@Param("desde") LocalDate desde, Pageable limite);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ElementoClicado(e.alvo, e.pagina, count(e)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.CLIQUE and e.alvo is not null and e.dia >= :desde "
            + "group by e.alvo, e.pagina order by count(e) desc")
    List<ElementoClicado> elementosMaisClicados(@Param("desde") LocalDate desde, Pageable limite);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ElementoClicado(e.alvo, e.pagina, count(e)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.CLIQUE and e.alvo is not null "
            + "and e.pagina = :pagina and e.dispositivo = :dispositivo and e.dia >= :desde "
            + "group by e.alvo, e.pagina order by count(e) desc")
    List<ElementoClicado> elementosDaPagina(@Param("pagina") String pagina, @Param("dispositivo") Dispositivo dispositivo,
                                            @Param("desde") LocalDate desde, Pageable limite);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$AtividadeHora(e.diaSemana, e.hora, count(e)) from EventoUso e "
            + "where e.tipo in (br.com.unisenai.smartrent.model.enums.TipoEventoUso.VISUALIZACAO, "
            + "br.com.unisenai.smartrent.model.enums.TipoEventoUso.CLIQUE) and e.dia >= :desde "
            + "group by e.diaSemana, e.hora")
    List<AtividadeHora> atividadePorHora(@Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ContagemRotulo(str(e.dispositivo), count(distinct e.sessaoId)) from EventoUso e "
            + "where e.dia >= :desde group by e.dispositivo order by count(distinct e.sessaoId) desc")
    List<ContagemRotulo> sessoesPorDispositivo(@Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ContagemRotulo(e.papel, count(distinct e.sessaoId)) from EventoUso e "
            + "where e.dia >= :desde group by e.papel order by count(distinct e.sessaoId) desc")
    List<ContagemRotulo> sessoesPorPapel(@Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$Permanencia(e.pagina, avg(e.valor), count(e)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.PERMANENCIA and e.dia >= :desde "
            + "group by e.pagina order by count(e) desc")
    List<Permanencia> permanenciaPorPagina(@Param("desde") LocalDate desde, Pageable limite);

    // ---- Mapa de calor de uma pagina/dispositivo ----

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$CelulaCalor(e.xCelula, e.yCelula, count(e)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.CLIQUE and e.xCelula is not null "
            + "and e.pagina = :pagina and e.dispositivo = :dispositivo and e.dia >= :desde "
            + "group by e.xCelula, e.yCelula")
    List<CelulaCalor> celulasDeCalor(@Param("pagina") String pagina, @Param("dispositivo") Dispositivo dispositivo,
                                     @Param("desde") LocalDate desde);

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ContagemRotulo(str(e.valor), count(e)) from EventoUso e "
            + "where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.ROLAGEM "
            + "and e.pagina = :pagina and e.dispositivo = :dispositivo and e.dia >= :desde "
            + "group by e.valor")
    List<ContagemRotulo> profundidadeRolagem(@Param("pagina") String pagina, @Param("dispositivo") Dispositivo dispositivo,
                                             @Param("desde") LocalDate desde);

    @Query("select count(e) from EventoUso e where e.tipo = br.com.unisenai.smartrent.model.enums.TipoEventoUso.VISUALIZACAO "
            + "and e.pagina = :pagina and e.dispositivo = :dispositivo and e.dia >= :desde")
    long visualizacoesDaPagina(@Param("pagina") String pagina, @Param("dispositivo") Dispositivo dispositivo,
                               @Param("desde") LocalDate desde);

    /** Altura tipica do documento nesta pagina/dispositivo, para dimensionar o mapa. */
    @Query("select avg(e.docAltura) from EventoUso e where e.pagina = :pagina and e.dispositivo = :dispositivo "
            + "and e.docAltura is not null and e.dia >= :desde")
    Double alturaMediaDoDocumento(@Param("pagina") String pagina, @Param("dispositivo") Dispositivo dispositivo,
                                  @Param("desde") LocalDate desde);

    /** Paginas que ja tiveram evento, das mais ativas para as menos (seletor do mapa de calor). */
    @Query("select e.pagina from EventoUso e where e.dia >= :desde group by e.pagina order by count(e) desc")
    List<String> paginasComEventos(@Param("desde") LocalDate desde, Pageable limite);

    // ---- Exportacao e retencao ----

    /** Leitura por chave (id crescente): exporta milhoes de linhas em lotes, sem offset e sem manter tudo na memoria. */
    @Query("select e from EventoUso e where e.id > :depoisDe and e.dia >= :desde order by e.id")
    List<EventoUso> lote(@Param("depoisDe") long depoisDe, @Param("desde") LocalDate desde, Pageable limite);

    @Modifying
    @Query("delete from EventoUso e where e.ocorridoEm < :limite")
    int apagarAnterioresA(@Param("limite") Instant limite);
}
