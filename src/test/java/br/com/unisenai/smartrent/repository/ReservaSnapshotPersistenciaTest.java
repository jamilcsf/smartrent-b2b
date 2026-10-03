package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** O snapshot e imutavel tambem na camada de persistencia, nao so por convencao dos servicos. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservas;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ReservaSnapshotPersistenciaTest {

    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private ImovelRepository imovelRepository;
    @Autowired
    private TestEntityManager em;

    private Reserva reservaPersistida() {
        Usuario gestor = new Usuario();
        gestor.setNome("Gestor");
        gestor.setEmail("g" + System.nanoTime() + "@smartrent.dev");
        gestor.setSenhaHash("x".repeat(60));
        gestor.setPapel(PapelUsuario.ANFITRIAO);
        gestor.setAtivo(true);
        em.persist(gestor);

        Imovel i = new Imovel();
        i.setUsuario(gestor);
        i.setTitulo("Apto");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(2);
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
        em.persist(i);

        Reserva r = new Reserva();
        r.setImovel(i);
        r.setHospedeNome("Maria");
        r.setHospedeEmail("m@e.com");
        r.setDataCheckin(LocalDate.of(2026, 11, 10));
        r.setDataCheckout(LocalDate.of(2026, 11, 12));
        r.setStatus(StatusReserva.CONFIRMADA);
        r.setNumeroDiarias(2);
        r.setPrecoDiariaSnapshot(new BigDecimal("300.00"));
        r.setTotalSnapshot(new BigDecimal("600.00"));
        r.setValorTotal(new BigDecimal("600.00"));
        r.setImovelTituloSnapshot("Apto");
        r.getPrecosDiarios().put(LocalDate.of(2026, 11, 10), new BigDecimal("300.00"));
        r.getPrecosDiarios().put(LocalDate.of(2026, 11, 11), new BigDecimal("300.00"));
        em.persist(r);
        em.flush();
        em.clear();
        return r;
    }

    @Test
    @DisplayName("CT182 - Snapshot e precos por data sao persistidos e relidos")
    void persisteSnapshot() {
        Reserva r = reservaPersistida();

        Reserva lida = reservaRepository.findById(r.getId()).orElseThrow();

        assertEquals(new BigDecimal("300.00"), lida.getPrecoDiariaSnapshot());
        assertEquals("BRL", lida.getMoeda());
        assertEquals(2, lida.getPrecosDiarios().size());
    }

    @Test
    @DisplayName("CT183 - Mexer nos campos do snapshot e salvar NAO altera o banco (colunas nao atualizaveis)")
    void snapshotNaoPodeSerAtualizado() {
        Reserva r = reservaPersistida();

        Reserva gerenciada = reservaRepository.findById(r.getId()).orElseThrow();
        gerenciada.setPrecoDiariaSnapshot(new BigDecimal("1.00"));
        gerenciada.setTotalSnapshot(new BigDecimal("2.00"));
        gerenciada.setNumeroDiarias(99);
        gerenciada.setImovelTituloSnapshot("Adulterado");
        gerenciada.setHospedeNome("Nome alterado"); // campo comum: este deve mudar
        reservaRepository.saveAndFlush(gerenciada);
        em.clear();

        Reserva relida = reservaRepository.findById(r.getId()).orElseThrow();
        assertEquals(new BigDecimal("300.00"), relida.getPrecoDiariaSnapshot());
        assertEquals(new BigDecimal("600.00"), relida.getTotalSnapshot());
        assertEquals(2, relida.getNumeroDiarias());
        assertEquals("Apto", relida.getImovelTituloSnapshot());
        assertEquals("Nome alterado", relida.getHospedeNome());
    }

    @Test
    @DisplayName("CT184 - Mudar o preco do imovel depois nao toca a reserva ja gravada")
    void precoDoImovelNaoAlteraReserva() {
        Reserva r = reservaPersistida();
        Imovel imovel = imovelRepository.findById(r.getImovel().getId()).orElseThrow();
        imovel.setValorDiariaBase(new BigDecimal("900.00"));
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        imovel.setEdicaoIniciadaEm(LocalDateTime.now());
        imovelRepository.saveAndFlush(imovel);
        em.clear();

        Reserva relida = reservaRepository.findById(r.getId()).orElseThrow();
        assertEquals(new BigDecimal("600.00"), relida.getValorTotal());
        assertEquals(new BigDecimal("300.00"), relida.getPrecoDiariaSnapshot());
        assertEquals(StatusReserva.CONFIRMADA, relida.getStatus(), "reserva confirmada preservada");
    }
}
