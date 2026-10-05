package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SmartChatMensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmartChatMensagemRepository extends JpaRepository<SmartChatMensagem, Long> {

    @Query("SELECT m FROM SmartChatMensagem m WHERE m.conversa.id = :conversaId AND m.id > :depoisDe ORDER BY m.id ASC")
    List<SmartChatMensagem> findDepoisDe(@Param("conversaId") Long conversaId, @Param("depoisDe") Long depoisDe, Pageable pagina);

    Optional<SmartChatMensagem> findFirstByConversaIdOrderByIdDesc(Long conversaId);

    boolean existsByChaveIdempotencia(String chave);

    /** Mensagens de pessoas (nao de sistema) enviadas pelo outro lado e ainda nao lidas por quem consulta. */
    @Query("SELECT COUNT(m) FROM SmartChatMensagem m WHERE m.conversa.id = :conversaId "
         + "AND m.tipo = br.com.unisenai.smartrent.model.enums.TipoMensagem.NORMAL "
         + "AND m.autor.id <> :usuarioId AND m.lidaEm IS NULL")
    long contarNaoLidas(@Param("conversaId") Long conversaId, @Param("usuarioId") Long usuarioId);

    @Query("SELECT COUNT(m) FROM SmartChatMensagem m WHERE (m.conversa.cliente.id = :usuarioId OR m.conversa.gestor.id = :usuarioId) "
         + "AND m.tipo = br.com.unisenai.smartrent.model.enums.TipoMensagem.NORMAL "
         + "AND m.autor.id <> :usuarioId AND m.lidaEm IS NULL")
    long contarNaoLidasDoUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SmartChatMensagem m SET m.lidaEm = :agora WHERE m.conversa.id = :conversaId "
         + "AND m.tipo = br.com.unisenai.smartrent.model.enums.TipoMensagem.NORMAL "
         + "AND m.autor.id <> :usuarioId AND m.lidaEm IS NULL")
    int marcarLidas(@Param("conversaId") Long conversaId, @Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    /** Conversas distintas que receberam do autor um texto com este resumo (HMAC) desde o instante. Nao toca nas colunas cifradas. */
    @Query("SELECT COUNT(DISTINCT m.conversa.id) FROM SmartChatMensagem m WHERE m.autor.id = :autorId "
         + "AND m.textoHmac = :hmac AND m.criadaEm >= :desde")
    long contarConversasComTextoIgual(@Param("autorId") Long autorId, @Param("hmac") String hmac, @Param("desde") Instant desde);

    /** Mensagens do autor marcadas como suspeita de fraude desde o instante (categorias e coluna em texto simples). */
    @Query("SELECT COUNT(m) FROM SmartChatMensagem m WHERE m.autor.id = :autorId "
         + "AND m.categorias LIKE '%SUSPEITA_FRAUDE%' AND m.criadaEm >= :desde")
    long contarSinalizadasComoFraude(@Param("autorId") Long autorId, @Param("desde") Instant desde);

    List<SmartChatMensagem> findByConversaIdAndIdIn(Long conversaId, Collection<Long> ids);
}
