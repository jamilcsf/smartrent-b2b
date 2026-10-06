package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.security.CifraCampo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Cifra as linhas do SmartChat gravadas em texto puro antes da cifra em repouso (ADR-006).
 *
 * <p>Idempotente: so toca em texto puro (sem prefixo de versao, ou com prefixo que nao decifra, como o legado "v1: teste"), entao pode rodar quantas vezes for preciso.
 * Percorre por id, em lotes, com uma transacao por lote. Usa JDBC de proposito: via JPA o conversor
 * decifraria e recifraria tudo, e o dirty checking nem enxergaria a diferenca. Registra so contagens.
 */
@Component
public class MigracaoCifraChat implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracaoCifraChat.class);
    static final int TAMANHO_LOTE = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final CifraCampo cifra;
    private final ChatProperties props;

    public MigracaoCifraChat(JdbcTemplate jdbc, PlatformTransactionManager tm, CifraCampo cifra, ChatProperties props) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(tm);
        this.cifra = cifra;
        this.props = props;
    }

    /** Linhas e valores cifrados por esta execucao (so contagens). */
    public record Resultado(long linhasExaminadas, long linhasAlteradas, long valoresCifrados) {
        Resultado somar(Resultado o) {
            return new Resultado(linhasExaminadas + o.linhasExaminadas, linhasAlteradas + o.linhasAlteradas,
                    valoresCifrados + o.valoresCifrados);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.migrarCifraAoIniciar()) {
            return;
        }
        try {
            Resultado r = executar();
            log.info("Cifra do SmartChat: {} linha(s) examinada(s), {} alterada(s), {} valor(es) cifrado(s).",
                    r.linhasExaminadas(), r.linhasAlteradas(), r.valoresCifrados());
        } catch (RuntimeException e) {
            // Sem a excecao inteira: a mensagem de um erro de SQL pode trazer valores. Leitura segue valida (legado passa).
            log.error("Cifra do SmartChat interrompida ({}); sera retomada na proxima execucao.", e.getClass().getSimpleName());
        }
    }

    public Resultado executar() {
        return migrar("smartchat_mensagens", "texto_filtrado", "texto_original")
                .somar(migrar("smartchat_denuncias", "descricao"))
                .somar(migrar("notificacoes", "mensagem"));
    }

    private Resultado migrar(String tabela, String... colunas) {
        String selecao = "select id, " + String.join(", ", colunas) + " from " + tabela + " where id > ? order by id limit ?";
        StringBuilder sets = new StringBuilder();
        for (String c : colunas) {
            sets.append(sets.length() == 0 ? "" : ", ").append(c).append(" = ?");
        }
        String atualizacao = "update " + tabela + " set " + sets + " where id = ?";

        Resultado total = new Resultado(0, 0, 0);
        long ultimoId = 0;
        while (true) {
            final long depoisDe = ultimoId;
            List<Object[]> lote = jdbc.query(selecao, (rs, i) -> {
                Object[] linha = new Object[colunas.length + 1];
                linha[0] = rs.getLong(1);
                for (int c = 0; c < colunas.length; c++) {
                    linha[c + 1] = rs.getString(c + 2);
                }
                return linha;
            }, depoisDe, TAMANHO_LOTE);
            if (lote.isEmpty()) {
                return total;
            }
            Resultado doLote = transacao.execute(status -> {
                long alteradas = 0;
                long cifrados = 0;
                for (Object[] linha : lote) {
                    Object[] params = new Object[colunas.length + 1];
                    boolean mudou = false;
                    for (int c = 0; c < colunas.length; c++) {
                        String valor = (String) linha[c + 1];
                        String contexto = tabela + "." + colunas[c]; // o mesmo AAD dos conversores JPA
                        if (valor != null && cifra.ehTextoPuro(valor, contexto)) {
                            params[c] = cifra.cifrar(valor, contexto);
                            mudou = true;
                            cifrados++;
                        } else {
                            params[c] = valor;
                        }
                    }
                    if (mudou) {
                        params[colunas.length] = linha[0];
                        jdbc.update(atualizacao, params);
                        alteradas++;
                    }
                }
                return new Resultado(lote.size(), alteradas, cifrados);
            });
            total = total.somar(doLote);
            ultimoId = (Long) lote.get(lote.size() - 1)[0];
        }
    }
}
