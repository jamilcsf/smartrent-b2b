package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.DenunciaChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface DenunciaChatRepository extends JpaRepository<DenunciaChat, Long> {

    /** Duplicata identica (mesmo par, conversa e motivo) dentro da janela: rejeitada para nao gerar spam. */
    boolean existsByConversaIdAndDenuncianteIdAndMotivoAndCriadaEmAfter(Long conversaId, Long denuncianteId, String motivo, Instant depois);
}
