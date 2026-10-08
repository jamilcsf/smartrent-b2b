package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** As listagens do painel escapam a busca; as acoes sobre contas ficam no ModeracaoService (ModeracaoServiceTest). */
class AdminServiceTest {

    @Test
    @DisplayName("CT1120 - Busca das listagens: % e _ digitados nao viram curinga; vazio nao filtra; tamanho limitado")
    void buscaSegura() {
        assertNull(AdminService.padrao("   "));
        assertNull(AdminService.padrao(null));
        assertEquals("%100\\%\\_ok%", AdminService.padrao(" 100%_OK "));
        assertEquals("%a\\\\b%", AdminService.padrao("a\\b"));
        assertEquals(82, AdminService.padrao("x".repeat(500)).length(), "80 caracteres + os dois %");
    }

    @Test
    @DisplayName("CT1124 - Visao geral: periodo fora da faixa e ajustado (1 a 365 dias) e a conversao nao divide por zero")
    void visaoGeralSemDados() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        TelemetriaAnaliseService telemetria = mock(TelemetriaAnaliseService.class);
        ReservaRepository reservas = mock(ReservaRepository.class);
        org.mockito.Mockito.when(reservas.valorMovimentadoDesde(org.mockito.ArgumentMatchers.any())).thenReturn(java.math.BigDecimal.ZERO);
        DataDeletionReviewService exclusoes = mock(DataDeletionReviewService.class);
        AdminService service = new AdminService(mock(UsuarioRepository.class), mock(ImovelRepository.class), reservas,
                exclusoes, mock(DenunciaChatRepository.class), telemetria, clock);
        var v = service.visaoGeral(99_999);
        assertEquals(365, v.dias());
        assertEquals(0.0, v.taxaConversao());
        assertEquals(1, service.visaoGeral(-4).dias());
    }
}
