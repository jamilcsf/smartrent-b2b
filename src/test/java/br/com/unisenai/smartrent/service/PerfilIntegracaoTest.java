package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.model.AuditoriaConta;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Perfil com banco de verdade (H2): nome de exibicao, copias em reservas e auditoria de conta. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:perfil;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({PerfilService.class, NomeExibicaoValidador.class, MessageFilterService.class,
        AuditoriaContaService.class, PerfilIntegracaoTest.Config.class})
class PerfilIntegracaoTest {

    @TestConfiguration
    static class Config {
        @Bean
        RelogioFalso relogio() {
            return new RelogioFalso(LocalDateTime.of(2026, 10, 3, 15, 0));
        }

        @Bean
        ChatProperties chatProperties() {
            return ChatProperties.padrao();
        }
    }

    @Autowired private TestEntityManager em;
    @Autowired private PerfilService perfilService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaContaRepository auditoriaRepository;

    private Usuario ana;
    private Usuario bia;

    private Usuario usuario(String nome, PapelUsuario papel) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(' ', '.') + System.nanoTime() + "@smartrent.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(papel);
        u.setAtivo(true);
        return em.persist(u);
    }

    @BeforeEach
    void preparar() {
        ana = usuario("Ana Rocha", PapelUsuario.CLIENTE);
        bia = usuario("Bia Souza", PapelUsuario.CLIENTE);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("CT445 - Alterar o nome atualiza so a propria conta, audita sem o valor e nao toca em reservas ja feitas")
    void alterarNome() {
        Usuario gestor = usuario("Gestor", PapelUsuario.ANFITRIAO);
        Imovel imovel = new Imovel();
        imovel.setUsuario(gestor);
        imovel.setTitulo("Apto");
        imovel.setTipoImovel(TipoImovel.APARTAMENTO);
        imovel.setCapacidadeHospedes(2);
        imovel.setNumeroQuartos(1);
        imovel.setNumeroBanheiros(1);
        imovel.setValorDiariaBase(new BigDecimal("100.00"));
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Floripa");
        e.setEstado("SC");
        e.setCep("88000-000");
        imovel.setEndereco(e);
        em.persist(imovel);

        Reserva r = new Reserva();
        r.setImovel(imovel);
        r.setCliente(ana);
        r.setHospedeNome("Ana Rocha");
        r.setHospedeEmail(ana.getEmail());
        r.setDataCheckin(LocalDate.of(2026, 12, 1));
        r.setDataCheckout(LocalDate.of(2026, 12, 3));
        r.setValorTotal(new BigDecimal("200.00"));
        r.setNumeroDiarias(2);
        r.setPrecoDiariaSnapshot(new BigDecimal("100.00"));
        r.setTotalSnapshot(new BigDecimal("200.00"));
        r.setImovelTituloSnapshot("Apto");
        r.setDataCriacao(LocalDateTime.of(2026, 10, 3, 12, 0));
        em.persist(r);
        em.flush();
        em.clear();

        PerfilResponse resposta = perfilService.atualizarNome(ana, "  Ana  Beatriz Rocha ", "10.0.0.1");
        em.flush();
        em.clear();

        assertEquals("Ana Beatriz Rocha", resposta.nome());
        assertEquals("Ana Beatriz Rocha", usuarioRepository.findById(ana.getId()).orElseThrow().getNome());
        assertEquals("Bia Souza", usuarioRepository.findById(bia.getId()).orElseThrow().getNome(), "outra conta intacta");
        assertEquals("Ana Rocha", em.find(Reserva.class, r.getId()).getHospedeNome(), "a reserva guarda a propria copia");

        List<AuditoriaConta> trilha = auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId());
        assertEquals(1, trilha.size());
        assertEquals(AuditoriaContaService.NOME_ALTERADO, trilha.get(0).getAcao());
        assertEquals("10.0.0.1", trilha.get(0).getIp());
        assertFalse(trilha.get(0).getDetalhes().contains("Beatriz"), "a auditoria nao carrega o valor");
    }

    @Test
    @DisplayName("CT446 - Nome invalido nao altera nada nem gera auditoria; mesmo nome nao audita")
    void nomeInvalidoOuIgual() {
        assertThrows(IllegalArgumentException.class, () -> perfilService.atualizarNome(ana, "<b>x</b>", "ip"));
        assertThrows(IllegalArgumentException.class, () -> perfilService.atualizarNome(ana, "Ana 48 99999-1234", "ip"));
        perfilService.atualizarNome(ana, "Ana Rocha", "ip");
        em.flush();
        em.clear();

        assertEquals("Ana Rocha", usuarioRepository.findById(ana.getId()).orElseThrow().getNome());
        assertTrue(auditoriaRepository.findByUsuarioIdOrderByIdDesc(ana.getId()).isEmpty());
    }
}
