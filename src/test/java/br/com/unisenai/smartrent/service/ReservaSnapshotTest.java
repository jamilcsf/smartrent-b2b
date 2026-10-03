package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Integridade de preco: a reserva carrega um snapshot imutavel das condicoes da criacao. */
@ExtendWith(MockitoExtension.class)
class ReservaSnapshotTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDate IN = LocalDate.of(2026, 11, 10);
    private static final LocalDate OUT = LocalDate.of(2026, 11, 13); // 3 diarias

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private br.com.unisenai.smartrent.repository.BloqueioDataRepository bloqueioRepository;
    @Mock
    private br.com.unisenai.smartrent.repository.UsuarioRepository usuarioRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher publicador;

    private ReservaService service;
    private Usuario gestor;
    private Imovel imovel;

    @BeforeEach
    void preparar() {
        service = new ReservaService(reservaRepository, imovelRepository,
                new ImovelAcesso(imovelRepository), new RelogioFalso(T0),
                br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties.padrao(),
                bloqueioRepository, usuarioRepository, publicador);
        gestor = new Usuario();
        gestor.setId(10L);
        gestor.setPapel(PapelUsuario.ANFITRIAO);

        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setUsuario(gestor);
        imovel.setTitulo("Apto Canasvieiras");
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        imovel.setAtivo(true);
        imovel.setValorDiariaBase(new BigDecimal("300.00"));
        imovel.setMetragemQuadrada(70);
        imovel.setNumeroQuartos(2);
        imovel.setNumeroBanheiros(1);
        imovel.setVagasGaragem(1);
        imovel.setCapacidadeHospedes(4);
        Endereco e = new Endereco();
        e.setLogradouro("Rua das Gaivotas");
        e.setNumero("120");
        e.setBairro("Canasvieiras");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        imovel.setEndereco(e);

        lenient().when(imovelRepository.findById(1L)).thenReturn(Optional.of(imovel));
        lenient().when(imovelRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(imovel));
        lenient().when(reservaRepository.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ReservaRequest pedido(BigDecimal valorEnviadoPeloCliente) {
        return new ReservaRequest(1L, "Maria", "maria@exemplo.com", null, IN, OUT,
                valorEnviadoPeloCliente, StatusReserva.CONFIRMADA, null, null);
    }

    @Test
    @DisplayName("CT170 - Ao criar, a reserva grava o snapshot: diaria por data, diarias, taxas, total e moeda")
    void criaComSnapshot() {
        Reserva r = service.criar(gestor, pedido(null));

        assertEquals(3, r.getNumeroDiarias());
        assertEquals(new BigDecimal("300.00"), r.getPrecoDiariaSnapshot());
        assertEquals(BigDecimal.ZERO, r.getTaxasSnapshot());
        assertEquals(new BigDecimal("900.00"), r.getTotalSnapshot());
        assertEquals(new BigDecimal("900.00"), r.getValorTotal());
        assertEquals("BRL", r.getMoeda());
        assertEquals(3, r.getPrecosDiarios().size());
        assertEquals(new BigDecimal("300.00"), r.getPrecosDiarios().get(IN));
        assertFalse(r.getPrecosDiarios().containsKey(OUT), "a noite do check-out nao e cobrada");
    }

    @Test
    @DisplayName("CT171 - O valor total enviado pelo cliente HTTP e ignorado: o total vem do snapshot")
    void valorDoClienteEIgnorado() {
        Reserva r = service.criar(gestor, pedido(new BigDecimal("1.00")));
        assertEquals(new BigDecimal("900.00"), r.getValorTotal());
        assertEquals(new BigDecimal("900.00"), r.getTotalSnapshot());
    }

    @Test
    @DisplayName("CT172 - Reserva criada ANTES da mudanca de preco mantem o preco antigo; a criada DEPOIS usa o novo")
    void mudancaDePrecoSoValeParaNovasReservas() {
        Reserva antes = service.criar(gestor, pedido(null));

        imovel.setValorDiariaBase(new BigDecimal("500.00")); // gestor reajusta o preco

        Reserva depois = service.criar(gestor, pedido(null));

        assertEquals(new BigDecimal("300.00"), antes.getPrecoDiariaSnapshot());
        assertEquals(new BigDecimal("900.00"), antes.getValorTotal());
        assertEquals(new BigDecimal("500.00"), depois.getPrecoDiariaSnapshot());
        assertEquals(new BigDecimal("1500.00"), depois.getValorTotal());
    }

    @Test
    @DisplayName("CT173 - Editar o anuncio (titulo, caracteristicas) nao descaracteriza o que foi reservado")
    void dadosDoImovelFicamNoSnapshot() {
        Reserva r = service.criar(gestor, pedido(null));

        imovel.setTitulo("Titulo totalmente diferente");
        imovel.setNumeroQuartos(5);
        imovel.getEndereco().setLogradouro("Outra Rua");

        assertEquals("Apto Canasvieiras", r.getImovelTituloSnapshot());
        assertEquals("Rua das Gaivotas, 120, Canasvieiras, Florianopolis/SC", r.getImovelEnderecoSnapshot());
        assertEquals("70 m² · 2 quarto(s) · 1 banheiro(s) · 1 vaga(s) · até 4 hóspedes",
                r.getImovelCaracteristicasSnapshot());
    }

    @Test
    @DisplayName("CT174 - Alterar as datas recalcula o total com a diaria do snapshot, nunca com o preco atual")
    void alterarDatasUsaPrecoDoSnapshot() {
        Reserva r = service.criar(gestor, pedido(null));
        r.setId(7L);
        when(reservaRepository.findById(7L)).thenReturn(Optional.of(r));
        imovel.setValorDiariaBase(new BigDecimal("999.00")); // preco subiu depois

        ReservaRequest novasDatas = new ReservaRequest(1L, "Maria", "maria@exemplo.com", null,
                IN, IN.plusDays(5), new BigDecimal("1.00"), null, null, null);
        Reserva atualizada = service.atualizar(gestor, 7L, novasDatas);

        assertEquals(new BigDecimal("1500.00"), atualizada.getValorTotal(), "5 x 300, nao 5 x 999");
        assertEquals(new BigDecimal("300.00"), atualizada.getPrecoDiariaSnapshot());
        assertEquals(new BigDecimal("900.00"), atualizada.getTotalSnapshot(), "total original preservado");
        assertEquals(3, atualizada.getNumeroDiarias(), "contrato original preservado");
    }

    @Test
    @DisplayName("CT175 - Atualizar reserva nao troca o imovel nem o snapshot")
    void naoTrocaImovel() {
        Reserva r = service.criar(gestor, pedido(null));
        r.setId(7L);
        when(reservaRepository.findById(7L)).thenReturn(Optional.of(r));

        ReservaRequest outroImovel = new ReservaRequest(2L, "Maria", "m@e.com", null, IN, OUT, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(gestor, 7L, outroImovel));
    }

    @Test
    @DisplayName("CT176 - Imovel fora do ar (em edicao, pre-publicacao, republicacao futura) nao recebe novas reservas")
    void foraDoArNaoRecebeReservas() {
        for (StatusAnuncio fora : new StatusAnuncio[]{StatusAnuncio.EM_EDICAO, StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO,
                StatusAnuncio.PRONTO_PARA_PUBLICAR, StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO}) {
            imovel.setStatus(fora);
            assertThrows(TransicaoInvalidaException.class, () -> service.criar(gestor, pedido(null)), fora.name());
        }
        imovel.setStatus(StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovel.setRepublicarEm(T0.plusMinutes(1));
        assertThrows(TransicaoInvalidaException.class, () -> service.criar(gestor, pedido(null)));

        imovel.setRepublicarEm(T0); // horario chegou: visivel, mesmo sem o job
        assertDoesNotThrow(() -> service.criar(gestor, pedido(null)));
    }

    @Test
    @DisplayName("CT177 - Reservas existentes seguem como estao quando o imovel sai do ar (nada as cancela)")
    void reservasExistentesPreservadas() {
        Reserva existente = service.criar(gestor, pedido(null));
        existente.setId(7L);
        when(reservaRepository.findById(7L)).thenReturn(Optional.of(existente));

        imovel.setStatus(StatusAnuncio.EM_EDICAO); // anuncio sai do ar

        assertEquals(StatusReserva.CONFIRMADA, existente.getStatus());
        assertEquals(new BigDecimal("900.00"), service.buscar(gestor, 7L).getValorTotal());
    }

    @Test
    @DisplayName("CT178 - Check-out igual ou anterior ao check-in e recusado")
    void periodoInvalido() {
        ReservaRequest invertido = new ReservaRequest(1L, "Maria", "m@e.com", null, OUT, IN, null, null, null, null);
        ReservaRequest igual = new ReservaRequest(1L, "Maria", "m@e.com", null, IN, IN, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.criar(gestor, invertido));
        assertThrows(IllegalArgumentException.class, () -> service.criar(gestor, igual));
    }

    @Test
    @DisplayName("CT179 - Choque de datas continua sendo recusado")
    void conflitoDeDatas() {
        when(reservaRepository.existeConflitoDeDatas(1L, IN, OUT)).thenReturn(true);
        var erro = assertThrows(IllegalArgumentException.class, () -> service.criar(gestor, pedido(null)));
        assertEquals("Conflito de datas detectado para este imovel.", erro.getMessage());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CT180 - Gestor nao cria, le, altera nem exclui reserva de imovel de outro gestor")
    void reservaDeOutroGestor() {
        Usuario outro = new Usuario();
        outro.setId(99L);
        outro.setPapel(PapelUsuario.ANFITRIAO);
        Reserva r = service.criar(gestor, pedido(null));
        r.setId(7L);
        when(reservaRepository.findById(7L)).thenReturn(Optional.of(r));

        assertThrows(AcessoNegadoException.class, () -> service.criar(outro, pedido(null)));
        assertThrows(AcessoNegadoException.class, () -> service.buscar(outro, 7L));
        assertThrows(AcessoNegadoException.class, () -> service.atualizar(outro, 7L, pedido(null)));
        assertThrows(AcessoNegadoException.class, () -> service.excluir(outro, 7L));
    }

    @Test
    @DisplayName("CT181 - Usuario comum (CLIENTE) nao opera reservas")
    void clienteNaoOperaReservas() {
        Usuario cliente = new Usuario();
        cliente.setId(5L);
        cliente.setPapel(PapelUsuario.CLIENTE);
        assertThrows(AcessoNegadoException.class, () -> service.listar(cliente));
        assertThrows(AcessoNegadoException.class, () -> service.criar(cliente, pedido(null)));
    }

    private ReservaRequest pedidoCom(LocalDate in, LocalDate out, Integer hospedes) {
        return new ReservaRequest(1L, "Maria", "maria@exemplo.com", null, in, out, null,
                StatusReserva.CONFIRMADA, null, null, hospedes);
    }

    @Test
    @DisplayName("CT200 - O total inclui a taxa de limpeza, cobrada uma vez por reserva")
    void totalIncluiTaxaDeLimpeza() {
        imovel.setTaxaLimpeza(new BigDecimal("80.00"));

        Reserva r = service.criar(gestor, pedido(null));

        assertEquals(new BigDecimal("980.00"), r.getTotalSnapshot()); // 3 x 300 + 80
        assertEquals(new BigDecimal("980.00"), r.getValorTotal());
        assertEquals(new BigDecimal("80.00"), r.getTaxasSnapshot());
        assertEquals(new BigDecimal("80.00"), r.getTaxaLimpezaSnapshot());
    }

    @Test
    @DisplayName("CT201 - Reserva com menos noites que o minimo e rejeitada pelo servidor")
    void rejeitaMenosNoitesQueOMinimo() {
        imovel.setMinimoDiarias(3);

        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.criar(gestor, pedidoCom(IN, IN.plusDays(2), 2)));

        assertTrue(erro.getMessage().contains("no mínimo 3 diárias"), erro.getMessage());
        verify(reservaRepository, never()).save(any());
        assertDoesNotThrow(() -> service.criar(gestor, pedidoCom(IN, IN.plusDays(3), 2)));
    }

    @Test
    @DisplayName("CT202 - Reserva com mais hospedes que o limite e rejeitada pelo servidor")
    void rejeitaMaisHospedesQueOLimite() {
        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.criar(gestor, pedidoCom(IN, OUT, 5))); // limite do imovel: 4

        assertTrue(erro.getMessage().contains("no máximo 4 hóspedes"), erro.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.criar(gestor, pedidoCom(IN, OUT, 0)));
        assertEquals(4, service.criar(gestor, pedidoCom(IN, OUT, 4)).getNumeroHospedes());
    }

    @Test
    @DisplayName("CT203 - O snapshot guarda minimo, taxa, limite e politica e nao muda quando o anuncio e editado")
    void snapshotDosTermosEImutavel() {
        imovel.setMinimoDiarias(2);
        imovel.setTaxaLimpeza(new BigDecimal("50.00"));
        Reserva r = service.criar(gestor, pedido(null));

        imovel.setMinimoDiarias(7);
        imovel.setTaxaLimpeza(new BigDecimal("500.00"));
        imovel.setCapacidadeHospedes(10);

        assertEquals(2, r.getMinimoDiariasSnapshot());
        assertEquals(new BigDecimal("50.00"), r.getTaxaLimpezaSnapshot());
        assertEquals(4, r.getLimiteHospedesSnapshot());
        assertEquals(48, r.getPoliticaAntecedenciaHoras());
        assertEquals(0, r.getPoliticaRegretDias());
        assertEquals("PROVISORIA-1", r.getPoliticaVersao());
        assertEquals(new BigDecimal("950.00"), r.getTotalSnapshot());
    }
}
