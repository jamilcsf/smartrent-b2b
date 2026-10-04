package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Reautenticacao das acoes sensiveis (trocar senha, trocar e-mail, pedir exclusao de dados):
 * exige a senha atual do usuario ou, para contas criadas pelo Google (sem senha conhecida),
 * uma credencial do Google recem-obtida cujo e-mail e o da conta.
 *
 * <p>Errar a senha atual e limitado (padrao: 5 erros por 15 minutos e usuario); passado o
 * limite, nem a senha certa e aceita ate a janela esvaziar. Nada que e digitado entra em log
 * ou na auditoria: so o fato de a tentativa ter sido recusada.
 */
@Service
public class ReautenticacaoService {

    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier google;
    private final LimitadorDeTaxa limitador;
    private final AuditoriaContaService auditoria;
    private final PerfilProperties props;

    public ReautenticacaoService(PasswordEncoder passwordEncoder, GoogleTokenVerifier google,
                                 LimitadorDeTaxa limitador, AuditoriaContaService auditoria, PerfilProperties props) {
        this.passwordEncoder = passwordEncoder;
        this.google = google;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.props = props;
    }

    /**
     * @param contexto texto curto e sem dados pessoais (ex.: "troca de senha") para a auditoria
     * @throws IllegalArgumentException credencial incorreta (HTTP 400: um 401 deslogaria o usuario no front)
     * @throws LimiteExcedidoException  tentativas demais (HTTP 429)
     */
    public void exigir(Usuario usuario, String senhaAtual, String credencialGoogle, String contexto, String ip) {
        String chave = "reauth:" + usuario.getId();
        Duration janela = Duration.ofMinutes(props.senhaJanelaMinutos());
        if (limitador.atingiu(chave, props.senhaTentativas(), janela)) {
            throw new LimiteExcedidoException("Muitas tentativas incorretas. Aguarde alguns minutos e tente de novo.");
        }
        if (!confere(usuario, senhaAtual, credencialGoogle)) {
            limitador.registrar(chave);
            auditoria.registrarIndependente(usuario.getId(), AuditoriaContaService.SENHA_TENTATIVA_RECUSADA,
                    "reautenticacao recusada: " + contexto, ip);
            throw new IllegalArgumentException(usuario.isSenhaDefinida()
                    ? "A senha atual está incorreta."
                    : "Não foi possível confirmar sua identidade com o Google.");
        }
        limitador.limpar(chave);
    }

    private boolean confere(Usuario usuario, String senhaAtual, String credencialGoogle) {
        if (usuario.isSenhaDefinida()) {
            return senhaAtual != null && !senhaAtual.isEmpty()
                    && passwordEncoder.matches(senhaAtual, usuario.getSenhaHash());
        }
        if (credencialGoogle == null || credencialGoogle.isBlank()) {
            return false;
        }
        try {
            return usuario.getEmail().equalsIgnoreCase(google.verificar(credencialGoogle).email());
        } catch (GoogleTokenVerifier.TokenGoogleInvalidoException e) {
            return false;
        }
    }
}
