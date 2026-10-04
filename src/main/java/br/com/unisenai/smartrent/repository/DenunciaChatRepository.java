package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.DenunciaChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface DenunciaChatRepository extends JpaRepository<DenunciaChat, Long> {

    /** Duplicata identica (mesmo par, conversa e motivo) dentro da janela: rejeitada para nao gerar spam. */
    boolean existsByConversaIdAndDenuncianteIdAndMotivoAndCriadaEmAfter(Long conversaId, Long denuncianteId, String motivo, Instant depois);

    /** Denuncias ainda nao resolvidas feitas CONTRA o usuario. */
    @Query("SELECT COUNT(d) FROM DenunciaChat d WHERE d.denunciado.id = :usuarioId AND d.status = 'PENDENTE'")
    long contarAbertasContra(@Param("usuarioId") Long usuarioId);

    /** Conversas do usuario (como cliente ou gestor) que tem alguma denuncia ainda nao resolvida: unica nocao de disputa que existe. */
    @Query("SELECT COUNT(DISTINCT d.conversa.id) FROM DenunciaChat d WHERE d.status = 'PENDENTE' "
         + "AND (d.conversa.cliente.id = :usuarioId OR d.conversa.gestor.id = :usuarioId)")
    long contarConversasEmDisputa(@Param("usuarioId") Long usuarioId);
}
