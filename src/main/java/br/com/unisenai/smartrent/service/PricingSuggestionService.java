package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;

import java.math.BigDecimal;

/**
 * Sugestao de preco por IA. Interface propositalmente pequena: o provedor e o
 * modelo (hoje Groq/Llama) podem ser trocados escrevendo outra implementacao,
 * sem tocar nas regras de pre-publicacao.
 *
 * <p>A implementacao so <em>sugere</em>. Nada e salvo nem confirmado aqui: o
 * gestor revisa e confirma pela rota de preco, que registra a origem "IA".
 */
public interface PricingSuggestionService {

    /**
     * @throws PricingIndisponivelException se a IA falhar, estourar o tempo ou
     *                                      devolver algo inutilizavel; a mensagem
     *                                      e apropriada para exibir ao gestor
     */
    Sugestao sugerir(Imovel imovel);

    record Sugestao(BigDecimal valor, String justificativa, String modelo) {
    }

    class PricingIndisponivelException extends RuntimeException {
        public PricingIndisponivelException(String mensagem) {
            super(mensagem);
        }

        public PricingIndisponivelException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
