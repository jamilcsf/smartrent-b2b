package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

/**
 * Regra de propriedade: o gestor so enxerga e altera os proprios imoveis.
 * Vive no servico, e nao no front nem so no papel, porque um gestor poderia
 * chamar a API com o id do imovel de outro.
 */
@Component
public class ImovelAcesso {

    private final ImovelRepository imovelRepository;

    public ImovelAcesso(ImovelRepository imovelRepository) {
        this.imovelRepository = imovelRepository;
    }

    public Imovel doGestor(Usuario gestor, Long imovelId) {
        Imovel imovel = imovelRepository.findById(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
        return conferir(gestor, imovel);
    }

    /** Igual a {@link #doGestor}, mas trava a linha: serializa operacoes concorrentes no mesmo imovel. */
    public Imovel doGestorParaAtualizar(Usuario gestor, Long imovelId) {
        Imovel imovel = imovelRepository.findByIdParaAtualizar(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
        return conferir(gestor, imovel);
    }

    /** Exige papel de gestor (ou admin) para operacoes que nao partem de um imovel existente. */
    public void exigirGestor(Usuario usuario) {
        if (usuario == null || (usuario.getPapel() != PapelUsuario.ANFITRIAO
                && usuario.getPapel() != PapelUsuario.ADMIN)) {
            throw new AcessoNegadoException("Apenas gestores de imóveis podem executar esta operação.");
        }
    }

    public Imovel conferir(Usuario gestor, Imovel imovel) {
        if (gestor == null) {
            throw new AcessoNegadoException("Autenticação necessária.");
        }
        boolean dono = imovel.getUsuario() != null
                && imovel.getUsuario().getId().equals(gestor.getId());
        boolean admin = gestor.getPapel() == PapelUsuario.ADMIN;
        boolean gestorDeImoveis = gestor.getPapel() == PapelUsuario.ANFITRIAO || admin;
        if (!gestorDeImoveis || !(dono || admin)) {
            throw new AcessoNegadoException("Você não tem permissão sobre este imóvel.");
        }
        return imovel;
    }
}
