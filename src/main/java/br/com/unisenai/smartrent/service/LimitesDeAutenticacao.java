package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Limites de login e cadastro, por IP e por e-mail, sobre o {@link LimitadorDeTaxa} (memoria, uma instancia).
 *
 * <p>So a falha de credencial conta contra o e-mail e o IP; um login correto zera o contador do e-mail.
 * O bloqueio por e-mail permite que um terceiro atrase o login de uma vitima por alguns minutos: troca aceita
 * contra a forca bruta, e o captcha continua exigido em toda tentativa.
 */
@Component
public class LimitesDeAutenticacao {

    static final Duration JANELA_LOGIN = Duration.ofMinutes(15);
    static final int FALHAS_POR_EMAIL = 5;
    static final int FALHAS_POR_IP = 20;
    static final Duration JANELA_CADASTRO = Duration.ofHours(1);
    static final int CADASTROS_POR_IP = 10;

    private static final String MENSAGEM_LOGIN = "Muitas tentativas de login. Tente novamente em alguns minutos.";

    private final LimitadorDeTaxa limitador;

    public LimitesDeAutenticacao(LimitadorDeTaxa limitador) {
        this.limitador = limitador;
    }

    /** Antes de consultar a senha: recusa quando o IP ou o e-mail ja acumularam falhas demais. */
    public void exigirLogin(String ip, String email) {
        if (limitador.atingiu(chaveIp(ip), FALHAS_POR_IP, JANELA_LOGIN)
                || limitador.atingiu(chaveEmail(email), FALHAS_POR_EMAIL, JANELA_LOGIN)) {
            throw new LimiteExcedidoException(MENSAGEM_LOGIN);
        }
    }

    public void registrarFalhaDeLogin(String ip, String email) {
        limitador.registrar(chaveIp(ip));
        limitador.registrar(chaveEmail(email));
    }

    public void registrarLoginValido(String email) {
        limitador.limpar(chaveEmail(email));
    }

    /** Cada tentativa de cadastro conta (com ou sem sucesso): contas em massa saem do mesmo IP. */
    public void exigirCadastro(String ip) {
        if (!limitador.permitir("cadastro:ip:" + ip, CADASTROS_POR_IP, JANELA_CADASTRO)) {
            throw new LimiteExcedidoException("Muitas contas criadas deste endereço. Tente novamente mais tarde.");
        }
    }

    private static String chaveIp(String ip) {
        return "login:ip:" + ip;
    }

    private static String chaveEmail(String email) {
        return "login:email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}
