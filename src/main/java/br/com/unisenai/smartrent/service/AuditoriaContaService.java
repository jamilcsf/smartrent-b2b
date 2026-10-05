package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.AuditoriaConta;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Escrita da auditoria de conta. Participa da transacao de quem chama (a trilha e a
 * mudanca se confirmam juntas); {@link #registrarIndependente} grava em transacao propria,
 * para eventos que precisam sobreviver a um rollback (tentativas recusadas, por exemplo).
 * Nunca recebe senha, hash ou token.
 */
@Service
public class AuditoriaContaService {

    public static final String NOME_ALTERADO = "NOME_ALTERADO";
    public static final String FOTO_ALTERADA = "FOTO_ALTERADA";
    public static final String FOTO_REMOVIDA = "FOTO_REMOVIDA";
    public static final String SENHA_ALTERADA = "SENHA_ALTERADA";
    public static final String SENHA_TENTATIVA_RECUSADA = "SENHA_TENTATIVA_RECUSADA";
    public static final String EMAIL_TROCA_SOLICITADA = "EMAIL_TROCA_SOLICITADA";
    public static final String EMAIL_TROCA_CONFIRMADA = "EMAIL_TROCA_CONFIRMADA";
    public static final String EMAIL_VERIFICACAO_ENVIADA = "EMAIL_VERIFICACAO_ENVIADA";
    public static final String EMAIL_VERIFICADO = "EMAIL_VERIFICADO";
    public static final String EXCLUSAO_SOLICITADA = "EXCLUSAO_SOLICITADA";
    public static final String EXCLUSAO_CANCELADA = "EXCLUSAO_CANCELADA";
    public static final String EXCLUSAO_DECIDIDA = "EXCLUSAO_DECIDIDA";
    public static final String RESTRICOES_INICIO = "RESTRICOES_INICIO";
    public static final String RESTRICOES_FIM = "RESTRICOES_FIM";
    public static final String LOGIN = "LOGIN";

    private final AuditoriaContaRepository repository;
    private final Clock clock;

    public AuditoriaContaService(AuditoriaContaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void registrar(Long usuarioId, String acao, String detalhes, String ip) {
        repository.save(new AuditoriaConta(usuarioId, acao, detalhes, ip, clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIndependente(Long usuarioId, String acao, String detalhes, String ip) {
        repository.save(new AuditoriaConta(usuarioId, acao, detalhes, ip, clock.instant()));
    }
}
