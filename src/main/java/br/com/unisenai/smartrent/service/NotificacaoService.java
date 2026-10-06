package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.NotificacaoResponse;
import br.com.unisenai.smartrent.model.Notificacao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.NotificacaoRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.AcessoOcultoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Notificacoes in-app do gestor (o sino do dashboard). */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Notificacao criar(Usuario destino, Long imovelId, String titulo, String mensagem, String link) {
        Notificacao n = new Notificacao();
        n.setUsuario(destino);
        n.setImovelId(imovelId);
        n.setTitulo(titulo.length() > 150 ? titulo.substring(0, 150) : titulo);
        n.setMensagem(mensagem.length() > 600 ? mensagem.substring(0, 600) : mensagem);
        n.setLink(link);
        n.setLida(false);
        n.setCriadaEm(Agora.de(clock));
        return repository.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(Usuario usuario) {
        return repository.findTop50ByUsuarioIdOrderByCriadaEmDesc(usuario.getId()).stream()
                .map(NotificacaoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public long naoLidas(Usuario usuario) {
        return repository.countByUsuarioIdAndLidaFalse(usuario.getId());
    }

    @Transactional
    public void marcarLida(Usuario usuario, Long id) {
        Notificacao n = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação não encontrada."));
        if (!n.getUsuario().getId().equals(usuario.getId())) {
            throw new AcessoOcultoException("Notificação não encontrada.");
        }
        n.setLida(true);
        repository.save(n);
    }
}
