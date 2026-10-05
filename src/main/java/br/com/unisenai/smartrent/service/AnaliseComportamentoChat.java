package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatSegurancaProperties;
import br.com.unisenai.smartrent.model.AlertaInterno;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import br.com.unisenai.smartrent.repository.AlertaInternoRepository;
import br.com.unisenai.smartrent.repository.SmartChatMensagemRepository;
import br.com.unisenai.smartrent.security.HmacTexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Analise de comportamento do SmartChat: envio em massa e suspeita de fraude. So gera alertas internos para a equipe
 * (consultados pelo {@link SinaisDeRisco}) e, no envio em massa, aplica um limite de envio mais restrito por tempo
 * configuravel. NAO suspende conta nem bloqueia mensagem. Nao guarda nem registra em log conteudo de mensagem: para
 * comparar textos usa so um HMAC-SHA-256 do texto normalizado.
 */
@Service
public class AnaliseComportamentoChat {

    private static final Logger log = LoggerFactory.getLogger(AnaliseComportamentoChat.class);

    /** Textos normalizados menores que isto ("ok", "obrigado") nao entram na comparacao: sao repetidos por natureza. */
    static final int TEXTO_MINIMO = 8;

    private final SmartChatMensagemRepository mensagens;
    private final AlertaInternoRepository alertas;
    private final HmacTexto hmac;
    private final ChatSegurancaProperties props;
    private final Clock clock;

    public AnaliseComportamentoChat(SmartChatMensagemRepository mensagens, AlertaInternoRepository alertas, HmacTexto hmac,
                                    ChatSegurancaProperties props, Clock clock) {
        this.mensagens = mensagens;
        this.alertas = alertas;
        this.hmac = hmac;
        this.props = props;
        this.clock = clock;
    }

    /** Resumo (HMAC do texto normalizado) para gravar na mensagem; nulo se o texto for curto demais para comparar. */
    public String resumoDe(String texto) {
        String normalizado = HmacTexto.normalizar(texto);
        return normalizado.length() < TEXTO_MINIMO ? null : hmac.resumo(normalizado);
    }

    public int limiteRestritoPorMinuto() {
        return props.limiteRestritoPorMinuto();
    }

    /** O usuario esta com o limite de envio restrito? (houve alerta de envio em massa dentro do tempo configurado) */
    public boolean limiteRestrito(Long usuarioId) {
        Instant desde = clock.instant().minus(Duration.ofMinutes(props.limiteRestritoMinutos()));
        return alertas.existsByUsuarioIdAndTipoAndCriadoEmAfter(usuarioId, TipoAlertaInterno.ENVIO_EM_MASSA, desde);
    }

    /** Chamar DEPOIS de gravar a mensagem. {@code resumo} e o devolvido por {@link #resumoDe}. */
    public void aposEnvio(Usuario autor, String resumo, boolean suspeitaFraude) {
        Instant agora = clock.instant();
        if (resumo != null) {
            Instant desde = agora.minus(Duration.ofMinutes(props.envioMassaMinutos()));
            long conversas = mensagens.contarConversasComTextoIgual(autor.getId(), resumo, desde);
            if (conversas >= limiar(autor)) {
                registrar(autor, TipoAlertaInterno.ENVIO_EM_MASSA, (int) conversas, desde, agora);
            }
        }
        if (suspeitaFraude) {
            Instant desde = agora.minus(Duration.ofMinutes(props.alertaJanelaMinutos()));
            registrar(autor, TipoAlertaInterno.SUSPEITA_FRAUDE,
                    (int) mensagens.contarSinalizadasComoFraude(autor.getId(), desde), desde, agora);
        }
    }

    /** Conta nova pesa mais: limiar menor. */
    int limiar(Usuario autor) {
        boolean contaNova = autor.getDataCriacao() != null
                && autor.getDataCriacao().isAfter(Agora.de(clock).minusDays(props.contaNovaDias()));
        return Math.max(2, contaNova ? props.envioMassaConversasContaNova() : props.envioMassaConversas());
    }

    /** Um alerta por usuario e tipo dentro da janela de alertas: nao duplica. */
    private void registrar(Usuario autor, TipoAlertaInterno tipo, int contagem, Instant inicio, Instant fim) {
        Instant corte = fim.minus(Duration.ofMinutes(props.alertaJanelaMinutos()));
        if (alertas.existsByUsuarioIdAndTipoAndCriadoEmAfter(autor.getId(), tipo, corte)) {
            return;
        }
        AlertaInterno a = new AlertaInterno();
        a.setUsuario(autor);
        a.setTipo(tipo);
        a.setContagem(contagem);
        a.setJanelaInicio(inicio);
        a.setJanelaFim(fim);
        a.setCriadoEm(fim);
        alertas.save(a);
        log.warn("Alerta interno {} criado para o usuario {} (contagem {}).", tipo, autor.getId(), contagem);
    }
}
