package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.VerificacaoEmail;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.repository.VerificacaoEmailRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Verificacao do e-mail da conta (pre-requisito para enviar mensagens no SmartChat). NAO e verificacao de identidade:
 * so prova que a pessoa le aquele e-mail.
 * <ul>
 *   <li>cadastro por senha: a conta nasce sem verificacao e recebe um link de uso unico e validade curta
 *       (token de 256 bits, so o SHA-256 no banco, reaproveitando {@link TokenSeguro} e {@link EmailSender});</li>
 *   <li>login pelo Google com e-mail verificado ja conta como verificado;</li>
 *   <li>reenvio com limite de taxa; um unico link pendente por usuario (o novo cancela o anterior);</li>
 *   <li>link invalido, vencido, usado ou cancelado: sempre a mesma mensagem.</li>
 * </ul>
 */
@Service
public class VerificacaoEmailService {

    private final VerificacaoEmailRepository repository;
    private final UsuarioRepository usuarios;
    private final AuditoriaContaService auditoria;
    private final EmailSender emailSender;
    private final TextosPoliticas textos;
    private final LimitadorDeTaxa limitador;
    private final PerfilProperties props;
    private final Clock clock;

    public VerificacaoEmailService(VerificacaoEmailRepository repository, UsuarioRepository usuarios,
                                   AuditoriaContaService auditoria, EmailSender emailSender, TextosPoliticas textos,
                                   LimitadorDeTaxa limitador, PerfilProperties props, Clock clock) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.emailSender = emailSender;
        this.textos = textos;
        this.limitador = limitador;
        this.props = props;
        this.clock = clock;
    }

    /** Gera o link e o envia ao e-mail da conta DEPOIS do commit. Usado no cadastro. */
    @Transactional
    public void enviarLink(Usuario usuario, String ip) {
        if (usuario.isEmailVerificado()) {
            return;
        }
        Instant agora = clock.instant();
        for (VerificacaoEmail antiga : repository.abertas(usuario.getId())) {
            antiga.setCanceladaEm(agora);
        }
        repository.flush(); // libera o indice unico parcial antes de inserir o novo

        String token = TokenSeguro.gerar();
        repository.save(new VerificacaoEmail(usuario.getId(), TokenSeguro.hash(token), agora,
                agora.plus(Duration.ofHours(props.emailVerificacaoHoras()))));
        auditoria.registrar(usuario.getId(), AuditoriaContaService.EMAIL_VERIFICACAO_ENVIADA,
                "link de verificacao de e-mail enviado", ip);

        String link = props.baseUrl().replaceAll("/+$", "") + "/verificar-email.html?token=" + token;
        String para = usuario.getEmail();
        String nome = usuario.getNome();
        AposCommit.executar("link de verificacao de e-mail", () -> emailSender.enviar(para,
                textos.get("verificacao.email.assunto"),
                textos.get("verificacao.email.corpo", nome, props.emailVerificacaoHoras(), link)));
    }

    /** Reenvio pedido pelo proprio usuario logado, com limite de taxa. */
    @Transactional
    public String reenviar(Usuario sessao, String ip) {
        if (!limitador.permitir("email-verificacao:" + sessao.getId(), props.emailPedidosPorHora(), Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitos pedidos de reenvio. Tente novamente mais tarde.");
        }
        Usuario u = usuarios.findById(sessao.getId()).orElse(sessao);
        if (u.isEmailVerificado()) {
            return "Seu e-mail já está verificado.";
        }
        enviarLink(u, ip);
        return textos.get("verificacao.email.reenviado");
    }

    /**
     * Confirma o link. Nao exige login (quem clica pode estar em outro navegador); o token de uso unico e a prova.
     * Link invalido, vencido, usado ou cancelado: mesma mensagem.
     */
    @Transactional
    public void confirmar(String token, String ip) {
        if (!limitador.permitir("email-verificar:" + ip, 20, Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitas tentativas. Tente novamente mais tarde.");
        }
        if (token == null || token.isBlank() || token.length() > 200) {
            throw linkInvalido();
        }
        Instant agora = clock.instant();
        VerificacaoEmail v = repository.findPorHashParaAtualizar(TokenSeguro.hash(token.trim()))
                .filter(x -> x.pendente(agora))
                .orElseThrow(VerificacaoEmailService::linkInvalido);
        Usuario u = usuarios.findById(v.getUsuarioId()).orElseThrow(VerificacaoEmailService::linkInvalido);
        v.setUsadoEm(agora);
        if (!u.isEmailVerificado()) {
            u.setEmailVerificadoEm(agora);
            usuarios.save(u);
        }
        auditoria.registrar(u.getId(), AuditoriaContaService.EMAIL_VERIFICADO, "e-mail da conta verificado pelo link", ip);
    }

    /** Marca como verificado sem link: e-mail ja verificado pelo Google ou confirmado na troca de e-mail. */
    public void marcarVerificado(Usuario usuario) {
        if (!usuario.isEmailVerificado()) {
            usuario.setEmailVerificadoEm(clock.instant());
        }
    }

    private static IllegalArgumentException linkInvalido() {
        return new IllegalArgumentException("Este link é inválido ou expirou. Peça um novo link de verificação no SmartChat ou no seu perfil.");
    }
}
