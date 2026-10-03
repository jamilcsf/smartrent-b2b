package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioPedido;
import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.BloqueioDataRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reserva e bloqueio simultaneos nas mesmas datas: a linha do imovel e travada
 * pelas duas operacoes e a checagem de conflito corre na mesma transacao, entao
 * exatamente UMA delas e aceita (nunca as duas, nunca nenhuma por falha de lock).
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concorrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({ReservaService.class, ReservaClienteMapper.class, CancelamentoService.class, ReembolsoService.class,
        RefundPolicyService.class, BloqueioService.class, AuditoriaService.class, NotificacaoService.class,
        ImovelAcesso.class, TextosPoliticas.class, NotificadorEmailLog.class, ReservasIntegracaoTest.Config.class})
class ConcorrenciaBloqueioReservaTest {

    @Autowired private PlatformTransactionManager tm;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ImovelRepository imoveis;
    @Autowired private ReservaRepository reservas;
    @Autowired private BloqueioDataRepository bloqueios;
    @Autowired private ReservaService reservaService;
    @Autowired private BloqueioService bloqueioService;

    @RepeatedTest(5)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CT350 - Reserva e bloqueio simultaneos nas mesmas datas: so um e aceito")
    void soUmDosDoisEAceito() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(tm);
        Usuario gestor = tx.execute(s -> {
            Usuario u = new Usuario();
            u.setNome("Gestor");
            u.setEmail("g" + System.nanoTime() + "@smartrent.dev");
            u.setSenhaHash("x".repeat(60));
            u.setPapel(PapelUsuario.ANFITRIAO);
            u.setAtivo(true);
            return usuarios.save(u);
        });
        Imovel imovel = tx.execute(s -> {
            Imovel i = new Imovel();
            i.setUsuario(gestor);
            i.setTitulo("Apto");
            i.setTipoImovel(TipoImovel.APARTAMENTO);
            i.setCapacidadeHospedes(4);
            i.setNumeroQuartos(1);
            i.setNumeroBanheiros(1);
            i.setValorDiariaBase(new BigDecimal("300.00"));
            i.setStatus(StatusAnuncio.PUBLICADO);
            Endereco e = new Endereco();
            e.setLogradouro("Rua A");
            e.setBairro("Centro");
            e.setCidade("Florianopolis");
            e.setEstado("SC");
            e.setCep("88000-000");
            i.setEndereco(e);
            return imoveis.save(i);
        });

        LocalDate in = LocalDate.of(2026, 12, 10);
        LocalDate out = LocalDate.of(2026, 12, 13);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Boolean> reservar = () -> {
            largada.await();
            try {
                reservaService.criar(gestor, new ReservaRequest(imovel.getId(), "Hospede", "h@e.com", null, in, out,
                        null, StatusReserva.CONFIRMADA, null, null, 2));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
        Callable<Boolean> bloquear = () -> {
            largada.await();
            try {
                bloqueioService.criar(gestor, imovel.getId(),
                        new BloqueioPedido(in, out.minusDays(1), "MANUTENCAO", null, false, false));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        };
        Future<Boolean> f1 = pool.submit(reservar);
        Future<Boolean> f2 = pool.submit(bloquear);
        largada.countDown();
        boolean reservou = f1.get();
        boolean bloqueou = f2.get();
        pool.shutdown();

        assertEquals(1, (reservou ? 1 : 0) + (bloqueou ? 1 : 0), "exatamente um dos dois deve ser aceito");
        assertEquals(reservou ? 1 : 0, reservas.findByImovelId(imovel.getId()).size());
        assertEquals(bloqueou ? 1 : 0, bloqueios.findByImovelIdOrderByDataInicio(imovel.getId()).size());
    }
}
