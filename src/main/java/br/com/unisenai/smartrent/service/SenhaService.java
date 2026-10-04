package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.SenhaRequest;
import br.com.unisenai.smartrent.dto.UsuarioResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.format.DateTimeFormatter;

/**
 * Troca de senha do usuario autenticado. Ordem: confirmacao confere, senha atual (ou Google)
 * confere, nova senha passa na politica e e diferente da atual. Ao trocar: grava o hash
 * (BCrypt, o mesmo algoritmo do cadastro), sobe a versao da sessao (os tokens de TODAS as
 * outras sessoes deixam de valer; a sessao atual recebe um token novo), audita o evento (sem
 * senha, sem hash) e avisa por e-mail depois do commit.
 *
 * <p>Continua permitida durante uma solicitacao de exclusao de dados: ajuda o dono legitimo a
 * retomar o controle da conta. Por isso nao passa pelo AccountRestrictionService.
 */
@Service
public class SenhaService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReautenticacaoService reautenticacao;
    private final PoliticaDeSenha politica;
    private final AuditoriaContaService auditoria;
    private final EmailSender emailSender;
    private final TextosPoliticas textos;
    private final JwtService jwtService;
    private final PerfilService perfilService;
    private final Clock clock;

    public SenhaService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                        ReautenticacaoService reautenticacao, PoliticaDeSenha politica, AuditoriaContaService auditoria,
                        EmailSender emailSender, TextosPoliticas textos, JwtService jwtService,
                        PerfilService perfilService, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.reautenticacao = reautenticacao;
        this.politica = politica;
        this.auditoria = auditoria;
        this.emailSender = emailSender;
        this.textos = textos;
        this.jwtService = jwtService;
        this.perfilService = perfilService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse alterar(Usuario sessao, SenhaRequest req, String ip) {
        Usuario u = perfilService.carregar(sessao);
        if (req.novaSenha() == null || !req.novaSenha().equals(req.confirmacaoSenha())) {
            throw new IllegalArgumentException("A confirmação não confere com a nova senha.");
        }
        reautenticacao.exigir(u, req.senhaAtual(), req.credencialGoogle(), "troca de senha", ip);
        politica.validar(req.novaSenha(), u.getEmail());
        if (u.isSenhaDefinida() && passwordEncoder.matches(req.novaSenha(), u.getSenhaHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual.");
        }

        boolean primeiraSenha = !u.isSenhaDefinida();
        u.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        u.setSenhaDefinida(true);
        u.setSessaoVersao(u.getSessaoVersao() + 1); // invalida os tokens das outras sessoes
        usuarioRepository.save(u);
        auditoria.registrar(u.getId(), AuditoriaContaService.SENHA_ALTERADA,
                (primeiraSenha ? "senha definida" : "senha alterada") + "; demais sessoes encerradas", ip);

        String nome = u.getNome();
        String email = u.getEmail();
        String quando = FORMATO.format(java.time.LocalDateTime.now(clock));
        AposCommit.executar("aviso de senha alterada", () -> emailSender.enviar(email,
                textos.get("perfil.email.senha-alterada.assunto"),
                textos.get("perfil.email.senha-alterada.corpo", nome, quando)));

        return new AuthResponse(jwtService.gerarToken(u), jwtService.getValidadeSegundos(), UsuarioResponse.de(u));
    }
}
