package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Imovel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImovelRepository extends JpaRepository<Imovel, Long>, JpaSpecificationExecutor<Imovel> {

    @Query("select new br.com.unisenai.smartrent.dto.TelemetriaDtos$ContagemRotulo(str(i.status), count(i)) from Imovel i group by i.status")
    List<ContagemRotulo> contagemPorStatus();


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
    List<Imovel> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<Imovel> findByUsuarioId(Long usuarioId);

    long countByUsuarioIdAndStatusIn(Long usuarioId, java.util.Collection<br.com.unisenai.smartrent.model.enums.StatusAnuncio> status);

    /**
     * Fonte unica da visibilidade publica. Alem de PUBLICADO, vale o
     * REPUBLICACAO_AGENDADA cujo horario ja chegou: assim um atraso ou falha do
     * job de republicacao nao deixa o anuncio fora do ar alem do prazo. Anuncio de gestor com conta suspensa
     * (moderacao, ADR-010) sai do catalogo junto com a conta.
     */
    String VISIVEL = "i.ativo = true and i.usuario.ativo = true and (i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.PUBLICADO "
            + "or (i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.REPUBLICACAO_AGENDADA "
            + "and i.republicarEm <= :agora))";

    @EntityGraph(attributePaths = "comodidades")
    @Query("select i from Imovel i where " + VISIVEL + " order by i.id")
    List<Imovel> findVisiveis(@Param("agora") LocalDateTime agora);

    @EntityGraph(attributePaths = "comodidades")
    @Query("select i from Imovel i where i.id = :id and " + VISIVEL)
    Optional<Imovel> findVisivelPorId(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    /** Trava a linha para serializar operacoes que dependem de contagem (limite de midias). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Imovel i where i.id = :id")
    Optional<Imovel> findByIdParaAtualizar(@Param("id") Long id);

    List<Imovel> findByStatus(br.com.unisenai.smartrent.model.enums.StatusAnuncio status);

    /** Promocao idempotente: so toca linhas ainda vencidas e ainda agendadas. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Imovel i set i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.PUBLICADO, "
            + "i.republicarEm = null "
            + "where i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.REPUBLICACAO_AGENDADA "
            + "and i.republicarEm <= :agora")
    int promoverRepublicacoesVencidas(@Param("agora") LocalDateTime agora);

    /**
     * Igual a {@link #promoverRepublicacoesVencidas}, mas NAO republica anuncio de gestor com solicitacao
     * de exclusao de dados em andamento (republicar e uma das acoes restritas): o anuncio fica agendado e
     * a promocao seguinte o retoma quando as restricoes terminarem.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Imovel i set i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.PUBLICADO, "
            + "i.republicarEm = null "
            + "where i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.REPUBLICACAO_AGENDADA "
            + "and i.republicarEm <= :agora "
            + "and not exists (select s.id from SolicitacaoExclusao s where s.usuarioId = i.usuario.id and s.estado in ("
            + "br.com.unisenai.smartrent.model.enums.EstadoExclusao.PENDENTE, "
            + "br.com.unisenai.smartrent.model.enums.EstadoExclusao.EM_ANALISE, "
            + "br.com.unisenai.smartrent.model.enums.EstadoExclusao.APROVADA))")
    int promoverRepublicacoesVencidasSemRestritos(@Param("agora") LocalDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Imovel i set i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.PRONTO_PARA_PUBLICAR "
            + "where i.status = br.com.unisenai.smartrent.model.enums.StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO "
            + "and i.precoPrimeiraConfirmacaoEm <= :limite")
    int promoverProntosParaPublicar(@Param("limite") LocalDateTime limite);
}
