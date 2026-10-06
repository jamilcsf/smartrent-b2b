package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.security.CifraCampo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

/** Job de migracao da cifra (H2 em memoria): cifra so o que e texto puro e pode rodar varias vezes. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:migracaocifra;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class MigracaoCifraChatTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager tm;

    private final CifraCampo cifra = new CifraCampo(new byte[32]);
    private MigracaoCifraChat job;

    @BeforeEach
    void preparar() {
        job = new MigracaoCifraChat(jdbc, tm, cifra, ChatProperties.padrao());
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE"); // so as colunas de texto importam aqui
    }

    private long mensagem(String filtrado, String original) {
        jdbc.update("insert into smartchat_mensagens (conversa_id, tipo, texto_filtrado, texto_original, ocorrencias, criada_em, codigo_publico) "
                + "values (1, 'NORMAL', ?, ?, 0, current_timestamp, ?)", filtrado, original, java.util.UUID.randomUUID());
        return jdbc.queryForObject("select max(id) from smartchat_mensagens", Long.class);
    }

    private long denuncia(String descricao) {
        jdbc.update("insert into smartchat_denuncias (conversa_id, denunciante_id, denunciado_id, motivo, descricao, status, criada_em) "
                + "values (1, 1, 2, 'ASSEDIO', ?, 'PENDENTE', current_timestamp)", descricao);
        return jdbc.queryForObject("select max(id) from smartchat_denuncias", Long.class);
    }

    private long notificacao(String mensagem) {
        jdbc.update("insert into notificacoes (usuario_id, titulo, mensagem, lida, criada_em) "
                + "values (1, 'Nova mensagem', ?, false, current_timestamp)", mensagem);
        return jdbc.queryForObject("select max(id) from notificacoes", Long.class);
    }

    private String coluna(String tabela, String coluna, long id) {
        return jdbc.queryForObject("select " + coluna + " from " + tabela + " where id = ?", String.class, id);
    }

    @Test
    @DisplayName("cifra os valores em texto puro, preserva nulos e e idempotente")
    void idempotente() {
        long m1 = mensagem("Olá, tudo bem? 😀", "Olá, me liga no 48 99999-0000");
        long m2 = mensagem("só filtrado", null);
        String jaCifrado = cifra.cifrar("já protegido", "smartchat_mensagens.texto_filtrado");
        String jaCifradoOriginal = cifra.cifrar("já protegido", "smartchat_mensagens.texto_original");
        long m3 = mensagem(jaCifrado, jaCifradoOriginal);
        long d1 = denuncia("ele foi grosseiro");
        long d2 = denuncia(null);

        MigracaoCifraChat.Resultado primeira = job.executar();
        assertEquals(5, primeira.linhasExaminadas());
        assertEquals(3, primeira.linhasAlteradas());
        assertEquals(4, primeira.valoresCifrados());

        assertTrue(CifraCampo.estaCifrado(coluna("smartchat_mensagens", "texto_filtrado", m1)));
        assertEquals("Olá, tudo bem? 😀", cifra.decifrar(coluna("smartchat_mensagens", "texto_filtrado", m1), "smartchat_mensagens.texto_filtrado"));
        assertEquals("Olá, me liga no 48 99999-0000", cifra.decifrar(coluna("smartchat_mensagens", "texto_original", m1), "smartchat_mensagens.texto_original"));
        assertEquals("só filtrado", cifra.decifrar(coluna("smartchat_mensagens", "texto_filtrado", m2), "smartchat_mensagens.texto_filtrado"));
        assertNull(coluna("smartchat_mensagens", "texto_original", m2));
        assertEquals(jaCifrado, coluna("smartchat_mensagens", "texto_filtrado", m3));
        assertEquals("ele foi grosseiro", cifra.decifrar(coluna("smartchat_denuncias", "descricao", d1), "smartchat_denuncias.descricao"));
        assertNull(coluna("smartchat_denuncias", "descricao", d2));

        String depoisDaPrimeira = coluna("smartchat_mensagens", "texto_filtrado", m1);
        MigracaoCifraChat.Resultado segunda = job.executar();
        assertEquals(5, segunda.linhasExaminadas());
        assertEquals(0, segunda.linhasAlteradas());
        assertEquals(0, segunda.valoresCifrados());
        assertEquals(depoisDaPrimeira, coluna("smartchat_mensagens", "texto_filtrado", m1), "segunda execucao nao recifra");
    }

    @Test
    @DisplayName("cifra a previa das notificacoes e trata legado que comeca com vN: como texto puro")
    void notificacoesELegadoComPrefixo() {
        long n = notificacao("Olá, posso levar meu cachorro?");
        long m = mensagem("v1: teste legado", "v2: outro legado");

        MigracaoCifraChat.Resultado r = job.executar();
        assertEquals(3, r.valoresCifrados());
        assertEquals("Olá, posso levar meu cachorro?", cifra.decifrar(coluna("notificacoes", "mensagem", n), "notificacoes.mensagem"));
        assertTrue(CifraCampo.estaCifrado(coluna("notificacoes", "mensagem", n)));
        assertEquals("v1: teste legado", cifra.decifrar(coluna("smartchat_mensagens", "texto_filtrado", m), "smartchat_mensagens.texto_filtrado"));
        assertEquals("v2: outro legado", cifra.decifrar(coluna("smartchat_mensagens", "texto_original", m), "smartchat_mensagens.texto_original"));

        assertEquals(0, job.executar().valoresCifrados(), "segunda execucao nao altera nada");
    }

    @Test
    @DisplayName("percorre mais de um lote")
    void variosLotes() {
        int n = MigracaoCifraChat.TAMANHO_LOTE + 7;
        for (int i = 0; i < n; i++) {
            mensagem("texto " + i, null);
        }
        MigracaoCifraChat.Resultado r = job.executar();
        assertEquals(n, r.linhasAlteradas());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from smartchat_mensagens where texto_filtrado not like 'v2:%'", Long.class));
    }
}
