package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextoDadosServiceTest {

    private static final LocalDate DE = LocalDate.of(2026, 1, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 4, 1); // 90 dias

    private static Imovel imovel(long id, String preco) {
        Imovel i = new Imovel();
        i.setId(id);
        i.setValorDiariaBase(new BigDecimal(preco));
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        Endereco e = new Endereco();
        e.setCidade("Florianópolis");
        i.setEndereco(e);
        return i;
    }

    private static Reserva reserva(String in, String out, String diaria) {
        Reserva r = new Reserva();
        r.setDataCheckin(LocalDate.parse(in));
        r.setDataCheckout(LocalDate.parse(out));
        r.setPrecoDiariaSnapshot(new BigDecimal(diaria));
        return r;
    }

    @Test
    @DisplayName("Desempenho: ocupacao e diaria media ponderada pelas noites, recortando a janela")
    void desempenho() {
        // 10 noites a 100 e 10 noites a 200 (a 2a comeca 5 dias antes da janela: so 5 noites contam)
        var d = ContextoDadosService.desempenho(List.of(
                reserva("2026-01-10", "2026-01-20", "100"),
                reserva("2025-12-27", "2026-01-06", "200")), 1, DE, ATE);

        assertEquals(15, d.noitesOcupadas());
        assertEquals(90, d.noitesDisponiveis());
        assertEquals(new BigDecimal("133.33"), d.diariaMedia());
        assertEquals(15.0 / 90, d.ocupacao(), 1e-9);
    }

    @Test
    @DisplayName("Mercado: mediana (par e impar), faixa e ocupacao sobre todos os anuncios")
    void mercado() {
        var impar = ContextoDadosService.mercado(
                List.of(imovel(1, "300"), imovel(2, "100"), imovel(3, "200")), List.of(), DE, ATE);
        assertEquals(new BigDecimal("200"), impar.medianaAnunciada());
        assertEquals(new BigDecimal("100"), impar.minAnunciada());
        assertEquals(new BigDecimal("300"), impar.maxAnunciada());
        assertEquals(0, impar.desempenho().noitesOcupadas());
        assertNull(impar.desempenho().diariaMedia());

        var par = ContextoDadosService.mercado(
                List.of(imovel(1, "100"), imovel(2, "200"), imovel(3, "300"), imovel(4, "500")),
                List.of(reserva("2026-02-01", "2026-02-10", "250")), DE, ATE);
        assertEquals(new BigDecimal("250.00"), par.medianaAnunciada());
        assertEquals(9.0 / 360, par.desempenho().ocupacao(), 1e-9);
    }

    @Test
    @DisplayName("Privacidade: com menos de 3 anuncios semelhantes o panorama nao e informado")
    void amostraPequenaNaoVaza() {
        var reservas = mock(ReservaRepository.class);
        var imoveis = mock(ImovelRepository.class);
        when(imoveis.findSemelhantesNoAr(eq(1L), any(), any(), any()))
                .thenReturn(List.of(imovel(2, "150"), imovel(3, "160")));
        var clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);

        var ctx = new ContextoDadosService(reservas, imoveis, clock).montar(imovel(1, "200"));

        assertNull(ctx.mercado());
        assertNull(ctx.proprio()); // sem reservas do proprio imovel
        assertEquals("", ContextoDadosService.paraPrompt(ctx));
    }

    @Test
    @DisplayName("Prompt traz historico e mercado em numeros agregados, sem dado pessoal")
    void promptComDados() {
        var reservas = mock(ReservaRepository.class);
        var imoveis = mock(ImovelRepository.class);
        when(imoveis.findSemelhantesNoAr(eq(1L), any(), any(), any()))
                .thenReturn(List.of(imovel(2, "150"), imovel(3, "250"), imovel(4, "350")));
        when(reservas.findOcupacaoNoPeriodo(eq(List.of(1L)), any(), any()))
                .thenReturn(List.of(reserva("2026-03-01", "2026-03-10", "180")));
        when(reservas.findOcupacaoNoPeriodo(eq(List.of(2L, 3L, 4L)), any(), any()))
                .thenReturn(List.of(reserva("2026-03-01", "2026-03-28", "220")));
        var clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);

        var texto = ContextoDadosService.paraPrompt(
                new ContextoDadosService(reservas, imoveis, clock).montar(imovel(1, "200")));

        assertTrue(texto.contains("ocupação 10%"), texto);
        assertTrue(texto.contains("R$ 180.00"), texto);
        assertTrue(texto.contains("3 anúncios semelhantes"), texto);
        assertTrue(texto.contains("mediana R$ 250.00"), texto);
        assertTrue(texto.contains("ocupação média 10%"), texto);
    }

    @Test
    @DisplayName("Provedor: os dados da plataforma entram no prompt e uma falha na consulta nao derruba a IA")
    void provedorUsaOContexto() {
        var ctxService = mock(ContextoDadosService.class);
        var imovel = imovel(1, "200");
        var mercado = new ContextoDadosService.Mercado(3, new BigDecimal("250"), new BigDecimal("150"),
                new BigDecimal("350"), new ContextoDadosService.Desempenho(30, 270, new BigDecimal("220")));
        when(ctxService.montar(imovel)).thenReturn(new ContextoDadosService.Contexto(null, mercado));
        var clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var provider = new GroqPricingSuggestionProvider(RestClient.builder().build(), new ObjectMapper(),
                clock, "k", "m", ctxService);

        assertTrue(provider.descrever(imovel).contains("3 anúncios semelhantes"));

        when(ctxService.montar(imovel)).thenThrow(new IllegalStateException("banco fora"));
        assertFalse(provider.descrever(imovel).contains("Dados reais da plataforma"));
    }
}
