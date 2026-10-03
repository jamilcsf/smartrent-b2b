package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.dto.SugestaoPrecoLoteResponse;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.test.web.client.ExpectedCount;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Provedor de IA (Groq) e sugestao em lote: so sugere, falhas viram edicao manual, dados sensiveis nao saem. */
@ExtendWith(MockitoExtension.class)
class PrecificacaoIaTest {

    private static final String BASE = "https://groq.teste/v1";

    private MockRestServiceServer servidor;
    private GroqPricingSuggestionProvider provider;
    private Imovel imovel;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        provider = new GroqPricingSuggestionProvider(builder.build(), new ObjectMapper(),
                new RelogioFalso(LocalDateTime.of(2026, 1, 15, 9, 0)), "chave-secreta", "llama-teste");

        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setTitulo("Titulo com telefone 48 99999-0000");
        imovel.setDescricao("Descricao livre com dados pessoais");
        imovel.setTipoImovel(TipoImovel.APARTAMENTO);
        imovel.setMetragemQuadrada(70);
        imovel.setNumeroQuartos(2);
        imovel.setNumeroBanheiros(1);
        imovel.setVagasGaragem(1);
        imovel.setCapacidadeHospedes(4);
        imovel.setComodidades(List.of("Wi-Fi", "Piscina"));
        imovel.setWhatsappLink("https://wa.me/5548999990000");
        Endereco e = new Endereco();
        e.setLogradouro("Rua Secreta");
        e.setNumero("777");
        e.setCep("88054-123");
        e.setBairro("Canasvieiras");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        imovel.setEndereco(e);
    }

    private static String resposta(String conteudoJson) {
        return "{\"choices\":[{\"message\":{\"content\":" + new ObjectMapper().valueToTree(conteudoJson) + "}}]}";
    }

    @Test
    @DisplayName("CT190 - Resposta valida da IA vira sugestao com valor, justificativa e modelo")
    void sugestaoValida() {
        servidor.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer chave-secreta"))
                .andRespond(withSuccess(resposta("{\"valor\": 412.5, \"justificativa\": \"Alta temporada em Canasvieiras.\"}"),
                        MediaType.APPLICATION_JSON));

        var s = provider.sugerir(imovel);

        assertEquals(new BigDecimal("412.50"), s.valor());
        assertEquals("Alta temporada em Canasvieiras.", s.justificativa());
        assertEquals("llama-teste", s.modelo());
        servidor.verify();
    }

    @Test
    @DisplayName("CT191 - Valor em texto com formato brasileiro tambem e entendido")
    void valorEmTextoBrasileiro() {
        servidor.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(resposta("{\"valor\": \"R$ 1.250,90\", \"justificativa\": \"ok\"}"),
                        MediaType.APPLICATION_JSON));
        assertEquals(new BigDecimal("1250.90"), provider.sugerir(imovel).valor());
    }

    @Test
    @DisplayName("CT192 - Para a IA vao so atributos do imovel: nada de titulo, descricao, rua, numero, CEP ou WhatsApp")
    void naoEnviaDadosSensiveis() {
        servidor.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Canasvieiras"),
                        org.hamcrest.Matchers.containsString("Piscina"),
                        org.hamcrest.Matchers.containsString("70 m"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Rua Secreta")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("777")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("88054")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("99999")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wa.me")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dados pessoais")))))
                .andRespond(withSuccess(resposta("{\"valor\": 300, \"justificativa\": \"ok\"}"), MediaType.APPLICATION_JSON));

        provider.sugerir(imovel);

        servidor.verify();
    }

    @Test
    @DisplayName("CT193 - Falha HTTP da IA vira mensagem clara com saida para a edicao manual")
    void falhaHttp() {
        servidor.expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var erro = assertThrows(PricingSuggestionService.PricingIndisponivelException.class,
                () -> provider.sugerir(imovel));

        assertTrue(erro.getMessage().contains("manualmente"));
    }

    @Test
    @DisplayName("CT194 - Resposta que nao e o JSON esperado ou valor absurdo e tratada como indisponivel")
    void respostaInutilizavel() {
        servidor.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(resposta("isto nao e json"), MediaType.APPLICATION_JSON));
        assertThrows(PricingSuggestionService.PricingIndisponivelException.class, () -> provider.sugerir(imovel));

        servidor.reset();
        servidor.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(resposta("{\"valor\": -5, \"justificativa\": \"x\"}"), MediaType.APPLICATION_JSON));
        assertThrows(PricingSuggestionService.PricingIndisponivelException.class, () -> provider.sugerir(imovel));
    }

    @Test
    @DisplayName("CT195 - Sem chave configurada nem tenta a chamada")
    void semChave() {
        var semChave = new GroqPricingSuggestionProvider(RestClient.builder().baseUrl(BASE).build(),
                new ObjectMapper(), new RelogioFalso(LocalDateTime.of(2026, 1, 15, 9, 0)), "", "m");
        servidor.expect(ExpectedCount.never(), requestTo(BASE + "/chat/completions"));

        var erro = assertThrows(PricingSuggestionService.PricingIndisponivelException.class,
                () -> semChave.sugerir(imovel));

        assertTrue(erro.getMessage().contains("não está configurada"));
    }

    // ------------------------------------------------------------------- lote

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private PricingSuggestionService pricing;

    private Usuario gestor() {
        Usuario u = new Usuario();
        u.setId(10L);
        u.setPapel(PapelUsuario.ANFITRIAO);
        return u;
    }

    private Imovel imovelDe(Long id, Usuario dono, StatusAnuncio status) {
        Imovel i = new Imovel();
        i.setId(id);
        i.setUsuario(dono);
        i.setTitulo("Imovel " + id);
        i.setStatus(status);
        lenient().when(imovelRepository.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    @DisplayName("CT196 - Lote: cada imovel e independente; a falha de um vira edicao manual e os outros seguem")
    void loteComFalhaParcial() {
        Usuario g = gestor();
        Imovel a = imovelDe(1L, g, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        Imovel b = imovelDe(2L, g, StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO);
        Imovel c = imovelDe(3L, g, StatusAnuncio.PUBLICADO);
        when(pricing.sugerir(a)).thenReturn(new PricingSuggestionService.Sugestao(new BigDecimal("350.00"), "ok", "m"));
        when(pricing.sugerir(b)).thenThrow(new PricingSuggestionService.PricingIndisponivelException("IA fora do ar"));

        var resposta = new PrecificacaoLoteService(new ImovelAcesso(imovelRepository), pricing)
                .sugerir(g, List.of(1L, 2L, 3L));

        List<SugestaoPrecoLoteResponse.Item> itens = resposta.itens();
        assertEquals(new BigDecimal("350.00"), itens.get(0).valorSugerido());
        assertNull(itens.get(0).erro());
        assertNull(itens.get(1).valorSugerido());
        assertEquals("IA fora do ar", itens.get(1).erro());
        assertNotNull(itens.get(2).erro(), "publicado nao entra na sugestao em pre-publicacao");
        verify(pricing, never()).sugerir(c);
    }

    @Test
    @DisplayName("CT197 - A sugestao nao salva nada: nenhum imovel e alterado nem persistido")
    void sugestaoNaoSalva() {
        Usuario g = gestor();
        Imovel a = imovelDe(1L, g, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        when(pricing.sugerir(a)).thenReturn(new PricingSuggestionService.Sugestao(new BigDecimal("350.00"), "ok", "m"));

        new PrecificacaoLoteService(new ImovelAcesso(imovelRepository), pricing).sugerir(g, List.of(1L));

        assertNull(a.getValorDiariaBase());
        assertEquals(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO, a.getStatus());
        assertNull(a.getPrecoPrimeiraConfirmacaoEm());
        verify(imovelRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT198 - Um imovel de outro gestor no lote derruba o pedido inteiro, sem chamar a IA")
    void loteComImovelAlheio() {
        Usuario g = gestor();
        Usuario outro = new Usuario();
        outro.setId(99L);
        outro.setPapel(PapelUsuario.ANFITRIAO);
        imovelDe(1L, g, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        imovelDe(2L, outro, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);

        assertThrows(AcessoNegadoException.class, () ->
                new PrecificacaoLoteService(new ImovelAcesso(imovelRepository), pricing).sugerir(g, List.of(1L, 2L)));
        verifyNoInteractions(pricing);
    }

    @Test
    @DisplayName("CT199 - Lote vazio ou acima do maximo e recusado")
    void limitesDoLote() {
        var service = new PrecificacaoLoteService(new ImovelAcesso(imovelRepository), pricing);
        assertThrows(ValidacaoAnuncioException.class, () -> service.sugerir(gestor(), List.of()));
        assertThrows(ValidacaoAnuncioException.class, () -> service.sugerir(gestor(), null));
        List<Long> muitos = java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList();
        assertThrows(ValidacaoAnuncioException.class, () -> service.sugerir(gestor(), muitos));
    }
}
