package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.SmartChatConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmartChatConversaRepository extends JpaRepository<SmartChatConversa, Long> {

    Optional<SmartChatConversa> findByCodigoPublico(java.util.UUID codigoPublico);

    Optional<SmartChatConversa> findByClienteIdAndGestorIdAndImovelId(Long clienteId, Long gestorId, Long imovelId);

    /** Conversas em que o usuario e cliente ou gestor, da mais recente para a mais antiga; filtro opcional por imovel. */
    @Query("SELECT c FROM SmartChatConversa c WHERE (c.cliente.id = :usuarioId OR c.gestor.id = :usuarioId) "
         + "AND (:imovelId IS NULL OR c.imovel.id = :imovelId) "
         + "ORDER BY COALESCE(c.ultimaMensagemEm, c.criadaEm) DESC")
    List<SmartChatConversa> findDoUsuario(@Param("usuarioId") Long usuarioId, @Param("imovelId") Long imovelId);
}
