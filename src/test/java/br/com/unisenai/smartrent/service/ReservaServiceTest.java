package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private ReservaService reservaService;

    @Test
    @DisplayName("CT02 - Deve rejeitar reserva quando houver sobreposicao total de datas")
    void deveRejeitarReservaComSobreposicaoDeDatas() {
        Long imovelId = 1L;
        LocalDate checkin = LocalDate.now().plusDays(2);
        LocalDate checkout = LocalDate.now().plusDays(5);
        
        Reserva novaReserva = new Reserva();
        novaReserva.setImovelId(imovelId);
        novaReserva.setDataCheckin(checkin);
        novaReserva.setDataCheckout(checkout);

        when(reservaRepository.existeConflitoDeDatas(imovelId, checkin, checkout)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> reservaService.cadastrarReserva(novaReserva)
        );

        assertEquals("Conflito de datas detectado para este imovel.", exception.getMessage());
        verify(reservaRepository, never()).save(any());
    }
}