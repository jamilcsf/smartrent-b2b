package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Perfil do usuario autenticado. Todo metodo recebe o usuario da sessao e o recarrega do
 * banco: o objeto do filtro de seguranca esta desanexado e pode estar desatualizado.
 */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final NomeExibicaoValidador nomeValidador;
    private final AuditoriaContaService auditoria;

    public PerfilService(UsuarioRepository usuarioRepository, NomeExibicaoValidador nomeValidador,
                         AuditoriaContaService auditoria) {
        this.usuarioRepository = usuarioRepository;
        this.nomeValidador = nomeValidador;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PerfilResponse obter(Usuario sessao) {
        return montar(carregar(sessao));
    }

    /**
     * Altera so o nome de exibicao. Reservas (hospedeNome), pagamentos e auditorias ja gravados
     * guardam a propria copia e nao mudam.
     */
    @Transactional
    public PerfilResponse atualizarNome(Usuario sessao, String nomeInformado, String ip) {
        Usuario u = carregar(sessao);
        String nome = nomeValidador.validar(nomeInformado);
        if (!nome.equals(u.getNome())) {
            u.setNome(nome);
            usuarioRepository.save(u);
            auditoria.registrar(u.getId(), AuditoriaContaService.NOME_ALTERADO, "nome de exibicao atualizado", ip);
        }
        return montar(u);
    }

    Usuario carregar(Usuario sessao) {
        return usuarioRepository.findById(sessao.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }

    public PerfilResponse montar(Usuario u) {
        return new PerfilResponse(u.getNome(), u.getEmail(), u.getPapel(), UsuarioResponse.fotoUrl(u),
                u.getDataCriacao(), u.isSenhaDefinida(), null, List.of());
    }
}
