package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.ModeracaoDtos.ComunicadoRecebido;
import br.com.unisenai.smartrent.model.Comunicado;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.ComunicadoRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Caixa de avisos da plataforma, do ponto de vista do usuario (qualquer papel). So le e marca como lido os PROPRIOS
 * avisos; aviso de outra pessoa responde igual a aviso inexistente (404, mesma mensagem).
 */
@Service
public class ComunicadoService {

    private final ComunicadoRepository repository;
    private final Clock clock;

    public ComunicadoService(ComunicadoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ComunicadoRecebido> listar(Usuario usuario) {
        return repository.findTop50ByDestinatarioIdOrderByIdDesc(usuario.getId()).stream()
                .map(c -> new ComunicadoRecebido(c.getId(), c.getRemetenteNivel().rotulo, c.getAssunto(), c.getTexto(),
                        c.getCriadoEm(), c.getLidoEm() != null))
                .toList();
    }

    @Transactional(readOnly = true)
    public long naoLidos(Usuario usuario) {
        return repository.countByDestinatarioIdAndLidoEmIsNull(usuario.getId());
    }

    @Transactional
    public void marcarLido(Usuario usuario, Long id) {
        Comunicado c = repository.findById(id)
                .filter(x -> x.getDestinatario().getId().equals(usuario.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aviso não encontrado."));
        if (c.getLidoEm() == null) {
            c.setLidoEm(clock.instant());
            repository.save(c);
        }
    }
}
