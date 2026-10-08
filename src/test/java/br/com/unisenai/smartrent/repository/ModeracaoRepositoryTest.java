package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.AcaoModeracao;
import br.com.unisenai.smartrent.model.AlertaInterno;
import br.com.unisenai.smartrent.model.Comunicado;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.NivelRemetente;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAlertaInterno;
import br.com.unisenai.smartrent.model.enums.TipoAcaoModeracao;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Consultas da area de moderacao contra um banco real (H2 aqui, PostgreSQL em producao). */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:moderacao;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ModeracaoRepositoryTest {

    private static final Instant AGORA = Instant.parse("2026-10-08T15:00:00Z");

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private AlertaInternoRepository alertas;
    @Autowired
    private AcaoModeracaoRepository acoes;
    @Autowired
    private ComunicadoRepository comunicados;

    private Usuario admin;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        admin = novo("admin", PapelUsuario.ADMIN, true);
        cliente = novo("cliente", PapelUsuario.CLIENTE, true);
    }

    private Usuario novo(String nome, PapelUsuario papel, boolean ativo) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome + System.nanoTime() + "@t.dev");
        u.setSenhaHash("x".repeat(60));
        u.setPapel(papel);
        u.setAtivo(ativo);
        return usuarios.save(u);
    }

    private AlertaInterno alerta(StatusAlertaInterno status) {
        AlertaInterno a = new AlertaInterno();
        a.setUsuario(cliente);
        a.setTipo(TipoAlertaInterno.ENVIO_EM_MASSA);
        a.setContagem(5);
        a.setJanelaInicio(AGORA.minusSeconds(600));
        a.setJanelaFim(AGORA);
        a.setCriadoEm(AGORA);
        a.setStatus(status);
        return alertas.save(a);
    }

    @Test
    @DisplayName("CT1135 - Limite de envio: alerta DESCARTADO nao conta mais; ABERTO e REVISADO continuam contando")
    void descartadoNaoRestringe() {
        Instant desde = AGORA.minusSeconds(3600);
        AlertaInterno a = alerta(StatusAlertaInterno.ABERTO);
        assertTrue(alertas.existsByUsuarioIdAndTipoAndCriadoEmAfterAndStatusNot(cliente.getId(), TipoAlertaInterno.ENVIO_EM_MASSA, desde, StatusAlertaInterno.DESCARTADO));
        a.setStatus(StatusAlertaInterno.REVISADO);
        alertas.save(a);
        assertTrue(alertas.existsByUsuarioIdAndTipoAndCriadoEmAfterAndStatusNot(cliente.getId(), TipoAlertaInterno.ENVIO_EM_MASSA, desde, StatusAlertaInterno.DESCARTADO));
        a.setStatus(StatusAlertaInterno.DESCARTADO);
        alertas.save(a);
        assertFalse(alertas.existsByUsuarioIdAndTipoAndCriadoEmAfterAndStatusNot(cliente.getId(), TipoAlertaInterno.ENVIO_EM_MASSA, desde, StatusAlertaInterno.DESCARTADO));
        // a deduplicacao de alertas (um por janela) continua enxergando o alerta descartado
        assertTrue(alertas.existsByUsuarioIdAndTipoAndCriadoEmAfter(cliente.getId(), TipoAlertaInterno.ENVIO_EM_MASSA, desde));
    }

    @Test
    @DisplayName("CT1136 - Listas do painel: alertas por situacao, contas suspensas e decisao mais recente de um alerta")
    void listas() {
        alerta(StatusAlertaInterno.ABERTO);
        AlertaInterno revisado = alerta(StatusAlertaInterno.REVISADO);
        assertEquals(1, alertas.findByStatusOrderByIdDesc(StatusAlertaInterno.ABERTO, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, alertas.findAllByOrderByIdDesc(PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, alertas.countByStatus(StatusAlertaInterno.REVISADO));

        novo("suspenso", PapelUsuario.ANFITRIAO, false);
        assertEquals(1, usuarios.findByAtivoFalseOrderByIdDesc(PageRequest.of(0, 10)).getTotalElements());

        acoes.save(new AcaoModeracao(cliente, admin, TipoAcaoModeracao.ALERTA_REVISADO, "primeira", null, revisado.getId(), AGORA));
        acoes.save(new AcaoModeracao(cliente, admin, TipoAcaoModeracao.ALERTA_DESCARTADO, "segunda", null, revisado.getId(), AGORA.plusSeconds(5)));
        var decisao = acoes.findFirstByAlertaIdAndTipoInOrderByIdDesc(revisado.getId(),
                Set.of(TipoAcaoModeracao.ALERTA_REVISADO, TipoAcaoModeracao.ALERTA_DESCARTADO)).orElseThrow();
        assertEquals("segunda", decisao.getMotivo());
        assertEquals(2, acoes.findByUsuarioIdOrderByIdDesc(cliente.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @DisplayName("CT1137 - Avisos: a contagem de nao lidos e so do destinatario e cai quando ele le")
    void avisos() {
        Usuario outro = novo("outro", PapelUsuario.ANFITRIAO, true);
        Comunicado c = comunicados.save(new Comunicado(cliente, admin, NivelRemetente.SUPORTE, "Assunto", "Texto do aviso", null, AGORA));
        comunicados.save(new Comunicado(outro, admin, NivelRemetente.MODERACAO, "Outro", "Texto", null, AGORA));
        assertEquals(1, comunicados.countByDestinatarioIdAndLidoEmIsNull(cliente.getId()));
        assertEquals(1, comunicados.findTop50ByDestinatarioIdOrderByIdDesc(cliente.getId()).size());
        c.setLidoEm(AGORA);
        comunicados.save(c);
        assertEquals(0, comunicados.countByDestinatarioIdAndLidoEmIsNull(cliente.getId()));
        assertEquals(1, comunicados.countByDestinatarioIdAndLidoEmIsNull(outro.getId()));
    }
}
