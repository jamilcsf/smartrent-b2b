package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.LembreteEdicao;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consultas que sustentam a visibilidade do catalogo e as promocoes por
 * tempo, contra um banco real (H2 em memoria), nao contra mocks.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:imoveis;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ImovelRepositoryTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Autowired
    private ImovelRepository imovelRepository;
    @Autowired
    private LembreteEdicaoRepository lembreteRepository;
    @Autowired
    private TestEntityManager em;

    private Usuario gestor;

    @BeforeEach
    void preparar() {
        gestor = new Usuario();
        gestor.setNome("Gestor");
        gestor.setEmail("g" + System.nanoTime() + "@smartrent.dev");
        gestor.setSenhaHash("x".repeat(60));
        gestor.setPapel(PapelUsuario.ANFITRIAO);
        gestor.setAtivo(true);
        em.persist(gestor);
    }

    private Imovel imovel(String titulo, StatusAnuncio status, boolean ativo) {
        Imovel i = new Imovel();
        i.setUsuario(gestor);
        i.setTitulo(titulo);
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(2);
        i.setNumeroQuartos(1);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("100.00"));
        i.setStatus(status);
        i.setAtivo(ativo);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        return em.persist(i);
    }

    private List<String> titulosVisiveis() {
        em.flush();
        em.clear();
        return imovelRepository.findVisiveis(AGORA).stream().map(Imovel::getTitulo).collect(Collectors.toList());
    }

    @Test
    @DisplayName("CT160 - Catalogo: so PUBLICADO ativo; pre-publicacao, em edicao e inativo ficam de fora")
    void catalogoSoMostraPublicados() {
        imovel("publicado", StatusAnuncio.PUBLICADO, true);
        imovel("inativo", StatusAnuncio.PUBLICADO, false);
        imovel("sem preco", StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO, true);
        imovel("aguardando", StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, true);
        imovel("pronto", StatusAnuncio.PRONTO_PARA_PUBLICAR, true);
        imovel("em edicao", StatusAnuncio.EM_EDICAO, true);

        assertEquals(List.of("publicado"), titulosVisiveis());
    }

    @Test
    @DisplayName("CT161 - Fallback: REPUBLICACAO_AGENDADA vencida e visivel mesmo sem o job; a futura, nao")
    void fallbackDaConsultaParaRepublicacaoVencida() {
        Imovel vencida = imovel("vencida", StatusAnuncio.REPUBLICACAO_AGENDADA, true);
        vencida.setRepublicarEm(AGORA.minusSeconds(1));
        Imovel noLimite = imovel("no limite", StatusAnuncio.REPUBLICACAO_AGENDADA, true);
        noLimite.setRepublicarEm(AGORA);
        Imovel futura = imovel("futura", StatusAnuncio.REPUBLICACAO_AGENDADA, true);
        futura.setRepublicarEm(AGORA.plusMinutes(1));

        List<String> visiveis = titulosVisiveis();

        assertTrue(visiveis.contains("vencida"));
        assertTrue(visiveis.contains("no limite"));
        assertFalse(visiveis.contains("futura"));
    }

    @Test
    @DisplayName("CT162 - Detalhe publico: id de imovel fora do catalogo nao e encontrado")
    void detalheRespeitaVisibilidade() {
        Imovel emEdicao = imovel("em edicao", StatusAnuncio.EM_EDICAO, true);
        Imovel publicado = imovel("publicado", StatusAnuncio.PUBLICADO, true);
        em.flush();
        em.clear();

        assertTrue(imovelRepository.findVisivelPorId(emEdicao.getId(), AGORA).isEmpty());
        assertTrue(imovelRepository.findVisivelPorId(publicado.getId(), AGORA).isPresent());
    }

    @Test
    @DisplayName("CT163 - Job de republicacao: promove so as vencidas e e idempotente")
    void promocaoDeRepublicacaoIdempotente() {
        Imovel vencida = imovel("vencida", StatusAnuncio.REPUBLICACAO_AGENDADA, true);
        vencida.setRepublicarEm(AGORA.minusMinutes(5));
        Imovel futura = imovel("futura", StatusAnuncio.REPUBLICACAO_AGENDADA, true);
        futura.setRepublicarEm(AGORA.plusMinutes(5));
        em.flush();

        assertEquals(1, imovelRepository.promoverRepublicacoesVencidas(AGORA));
        assertEquals(0, imovelRepository.promoverRepublicacoesVencidas(AGORA), "segunda execucao nao muda nada");

        em.clear();
        assertEquals(StatusAnuncio.PUBLICADO, imovelRepository.findById(vencida.getId()).orElseThrow().getStatus());
        assertEquals(StatusAnuncio.REPUBLICACAO_AGENDADA, imovelRepository.findById(futura.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("CT164 - Nenhum job promove, republica ou altera imovel em EM_EDICAO, por mais tempo que passe")
    void emEdicaoNuncaEMexidoPorJobs() {
        Imovel emEdicao = imovel("em edicao", StatusAnuncio.EM_EDICAO, true);
        emEdicao.setEdicaoIniciadaEm(AGORA.minusDays(400));
        em.flush();
        LocalDateTime muitoDepois = AGORA.plusYears(5);

        imovelRepository.promoverRepublicacoesVencidas(muitoDepois);
        imovelRepository.promoverProntosParaPublicar(muitoDepois);

        em.clear();
        Imovel depois = imovelRepository.findById(emEdicao.getId()).orElseThrow();
        assertEquals(StatusAnuncio.EM_EDICAO, depois.getStatus());
        assertEquals(AGORA.minusDays(400), depois.getEdicaoIniciadaEm());
        assertFalse(imovelRepository.findVisiveis(muitoDepois).stream()
                .anyMatch(i -> i.getId().equals(emEdicao.getId())));
    }

    @Test
    @DisplayName("CT165 - Promocao AGUARDANDO -> PRONTO usa o instante da PRIMEIRA confirmacao e e idempotente")
    void promocaoParaPronto() {
        Imovel cumprida = imovel("cumprida", StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, true);
        cumprida.setPrecoPrimeiraConfirmacaoEm(AGORA.minusHours(24));
        Imovel faltando = imovel("faltando", StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, true);
        faltando.setPrecoPrimeiraConfirmacaoEm(AGORA.minusHours(23));
        em.flush();

        LocalDateTime limite = AGORA.minusHours(24);
        assertEquals(1, imovelRepository.promoverProntosParaPublicar(limite));
        assertEquals(0, imovelRepository.promoverProntosParaPublicar(limite));

        em.clear();
        assertEquals(StatusAnuncio.PRONTO_PARA_PUBLICAR, imovelRepository.findById(cumprida.getId()).orElseThrow().getStatus());
        assertEquals(StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO, imovelRepository.findById(faltando.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("CT166 - Gestor so lista os proprios imoveis")
    void listaPorGestor() {
        imovel("meu", StatusAnuncio.PUBLICADO, true);
        Usuario outro = new Usuario();
        outro.setNome("Outro");
        outro.setEmail("outro" + System.nanoTime() + "@smartrent.dev");
        outro.setSenhaHash("x".repeat(60));
        outro.setPapel(PapelUsuario.ANFITRIAO);
        outro.setAtivo(true);
        em.persist(outro);
        Imovel alheio = imovel("alheio", StatusAnuncio.PUBLICADO, true);
        alheio.setUsuario(outro);
        em.flush();
        em.clear();

        List<String> meus = imovelRepository.findByUsuarioIdOrderByIdDesc(gestor.getId()).stream()
                .map(Imovel::getTitulo).toList();
        assertEquals(List.of("meu"), meus);
    }

    @Test
    @DisplayName("CT167 - Chave unica do lembrete: o mesmo (imovel, edicao, numero, canal) nao entra duas vezes")
    void lembreteUnico() {
        Imovel i = imovel("em edicao", StatusAnuncio.EM_EDICAO, true);
        LocalDateTime inicio = AGORA.minusDays(2);
        lembreteRepository.saveAndFlush(lembrete(i, inicio, 1, "IN_APP"));
        lembreteRepository.saveAndFlush(lembrete(i, inicio, 1, "EMAIL_LOG")); // outro canal: ok
        lembreteRepository.saveAndFlush(lembrete(i, inicio, 2, "IN_APP"));    // outro numero: ok

        assertThrows(DataIntegrityViolationException.class,
                () -> lembreteRepository.saveAndFlush(lembrete(i, inicio, 1, "IN_APP")));
    }

    private LembreteEdicao lembrete(Imovel i, LocalDateTime inicio, int numero, String canal) {
        LembreteEdicao l = new LembreteEdicao();
        l.setImovel(i);
        l.setEdicaoIniciadaEm(inicio);
        l.setNumero(numero);
        l.setCanal(canal);
        l.setCriadoEm(AGORA);
        return l;
    }

    @Test
    @DisplayName("CT168 - findByIdParaAtualizar e findById devolvem o mesmo imovel")
    void bloqueioDeLinha() {
        Imovel i = imovel("x", StatusAnuncio.PUBLICADO, true);
        em.flush();
        em.clear();
        Optional<Imovel> travado = imovelRepository.findByIdParaAtualizar(i.getId());
        assertTrue(travado.isPresent());
    }
}
