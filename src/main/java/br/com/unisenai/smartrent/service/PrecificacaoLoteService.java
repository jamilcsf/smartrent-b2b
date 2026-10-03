package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.SugestaoPrecoLoteResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Sugestao de preco por IA em lote, a partir do Painel do Gestor.
 *
 * <p>So le e sugere: nenhuma sugestao e gravada. Confirmar e uma acao
 * explicita do gestor sobre cada imovel (rota de preco, origem IA).
 */
@Service
public class PrecificacaoLoteService {

    static final int MAXIMO_POR_LOTE = 20;

    private final ImovelAcesso acesso;
    private final PricingSuggestionService pricing;

    public PrecificacaoLoteService(ImovelAcesso acesso, PricingSuggestionService pricing) {
        this.acesso = acesso;
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public SugestaoPrecoLoteResponse sugerir(Usuario gestor, List<Long> ids) {
        acesso.exigirGestor(gestor);
        if (ids == null || ids.isEmpty()) {
            throw new ValidacaoAnuncioException("Selecione ao menos um imóvel.");
        }
        List<Long> unicos = new ArrayList<>(new LinkedHashSet<>(ids));
        if (unicos.size() > MAXIMO_POR_LOTE) {
            throw new ValidacaoAnuncioException("Selecione no máximo " + MAXIMO_POR_LOTE + " imóveis por vez.");
        }

        // Propriedade e checada para TODOS antes de chamar a IA: um id alheio
        // derruba o pedido inteiro com 403, sem gastar chamadas.
        List<Imovel> imoveis = unicos.stream().map(id -> acesso.doGestor(gestor, id)).toList();

        List<SugestaoPrecoLoteResponse.Item> itens = new ArrayList<>();
        for (Imovel i : imoveis) {
            if (!i.getStatus().prePublicacao()) {
                itens.add(item(i, null, null, null,
                        "A sugestão por IA vale para imóveis em pré-publicação. Altere o preço pela edição do anúncio."));
                continue;
            }
            try {
                var s = pricing.sugerir(i);
                itens.add(item(i, s.valor(), s.justificativa(), s.modelo(), null));
            } catch (PricingSuggestionService.PricingIndisponivelException e) {
                itens.add(item(i, null, null, null, e.getMessage()));
            }
        }
        return new SugestaoPrecoLoteResponse(itens);
    }

    private static SugestaoPrecoLoteResponse.Item item(Imovel i, java.math.BigDecimal valor, String justificativa,
                                                       String modelo, String erro) {
        return new SugestaoPrecoLoteResponse.Item(i.getId(), i.getTitulo(), i.getStatus(),
                i.getValorDiariaBase(), valor, justificativa, modelo, erro);
    }
}
