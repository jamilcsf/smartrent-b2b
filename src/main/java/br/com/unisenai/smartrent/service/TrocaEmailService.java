package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.model.TrocaEmail;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.TrocaEmailRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Troca do e-mail de login (que e o identificador da conta), com verificacao:
 * <ol>
 *   <li>pedido: exige reautenticacao, valida o formato e responde SEMPRE com a mesma mensagem
 *       neutra, exista ou nao uma conta com o endereco (nao ha como descobrir quem tem conta);</li>
 *   <li>so se o endereco estiver livre: gera token de uso unico (guarda so o hash, validade
 *       curta), envia o link ao NOVO e-mail e um aviso ao e-mail ANTIGO (depois do commit);</li>
 *   <li>confirmacao do link: reconfere a disponibilidade, troca o e-mail, sobe a versao da sessao
 *       (as outras sessoes caem), consome o token e audita.</li>
 * </ol>
 * Um unico pedido pendente por usuario (o novo cancela o anterior). Ate a confirmacao o login
 * continua com o e-mail antigo.
 */
@Service
public class TrocaEmailService {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)+$");
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final TrocaEmailRepository trocaRepository;
    private final PerfilService perfilService;
    private final ReautenticacaoService reautenticacao;
    private final AuditoriaContaService auditoria;
    private final EmailSender emailSender;
    private final TextosPoliticas textos;
    private final LimitadorDeTaxa limitador;
    private final PerfilProperties props;
    private final Clock clock;

    public TrocaEmailService(UsuarioRepository usuarioRepository, TrocaEmailRepository trocaRepository,
                             PerfilService perfilService, ReautenticacaoService reautenticacao,
                             AuditoriaContaService auditoria, EmailSender emailSender, TextosPoliticas textos,
                             LimitadorDeTaxa limitador, PerfilProperties props, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.trocaRepository = trocaRepository;
        this.perfilService = perfilService;
        this.reautenticacao = reautenticacao;
        this.auditoria = auditoria;
        this.emailSender = emailSender;
        this.textos = textos;
        this.limitador = limitador;
        this.props = props;
        this.clock = clock;
    }

    /** Mensagem unica devolvida ao usuario, qualquer que seja o resultado. */
    public String mensagemNeutra() {
        return textos.get("perfil.email.resposta-neutra", props.emailTokenMinutos());
    }

    @Transactional
    public String solicitar(Usuario sessao, String novoEmail, String senhaAtual, String credencialGoogle, String ip) {
        if (!limitador.permitir("email-pedido:" + sessao.getId(), props.emailPedidosPorHora(), Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitos pedidos de troca de e-mail. Tente novamente mais tarde.");
        }
        Usuario u = perfilService.carregar(sessao);
        String email = normalizar(novoEmail);
        reautenticacao.exigir(u, senhaAtual, credencialGoogle, "troca de e-mail", ip);
        if (email.equalsIgnoreCase(u.getEmail())) {
            throw new IllegalArgumentException("O novo e-mail é igual ao atual.");
        }

        Instant agora = clock.instant();
        // Um pedido pendente por usuario: o novo substitui o anterior.
        for (TrocaEmail antiga : trocaRepository.abertas(u.getId())) {
            antiga.setCanceladaEm(agora);
        }
        trocaRepository.flush(); // libera o indice unico parcial antes de inserir o novo

        boolean livre = usuarioRepository.findByEmail(email).isEmpty();
        auditoria.registrar(u.getId(), AuditoriaContaService.EMAIL_TROCA_SOLICITADA,
                livre ? "pedido de troca de e-mail registrado" : "pedido recusado em silencio (endereco indisponivel)", ip);
        if (livre) {
            String token = TokenSeguro.gerar();
            trocaRepository.save(new TrocaEmail(u.getId(), email, TokenSeguro.hash(token), agora,
                    agora.plus(Duration.ofMinutes(props.emailTokenMinutos()))));
            String link = props.baseUrl().replaceAll("/+$", "") + "/confirmar-email.html?token=" + token;
            String nome = u.getNome();
            String emailAntigo = u.getEmail();
            String quando = QUANDO.format(LocalDateTime.now(clock));
            AposCommit.executar("link de confirmacao de e-mail", () -> emailSender.enviar(email,
                    textos.get("perfil.email.confirmar.assunto"),
                    textos.get("perfil.email.confirmar.corpo", nome, props.emailTokenMinutos(), link)));
            AposCommit.executar("aviso ao e-mail antigo", () -> emailSender.enviar(emailAntigo,
                    textos.get("perfil.email.troca-solicitada-antigo.assunto"),
                    textos.get("perfil.email.troca-solicitada-antigo.corpo", nome, quando)));
        }
        return mensagemNeutra();
    }

    /**
     * Confirma o link. Nao exige login (quem clica no e-mail pode estar em outro navegador); o
     * token de uso unico e a prova. Link invalido, vencido, usado ou cancelado: mesma mensagem.
     */
    @Transactional
    public void confirmar(String token, String ip) {
        if (!limitador.permitir("email-confirmar:" + ip, 20, Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitas tentativas. Tente novamente mais tarde.");
        }
        if (token == null || token.isBlank() || token.length() > 200) {
            throw linkInvalido();
        }
        Instant agora = clock.instant();
        TrocaEmail troca = trocaRepository.findPorHashParaAtualizar(TokenSeguro.hash(token.trim()))
                .filter(t -> t.pendente(agora))
                .orElseThrow(TrocaEmailService::linkInvalido);
        Usuario u = usuarioRepository.findById(troca.getUsuarioId()).orElseThrow(TrocaEmailService::linkInvalido);

        // O endereco pode ter sido tomado por outra conta depois do pedido.
        if (usuarioRepository.findByEmail(troca.getEmailNovo()).isPresent()) {
            throw new IllegalArgumentException("Este e-mail não está mais disponível. Faça um novo pedido com outro endereço.");
        }
        String emailAntigo = u.getEmail();
        u.setEmail(troca.getEmailNovo());
        u.setSessaoVersao(u.getSessaoVersao() + 1); // as sessoes abertas (token com o e-mail antigo) caem
        troca.setUsadoEm(agora);
        try {
            usuarioRepository.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Este e-mail não está mais disponível. Faça um novo pedido com outro endereço.");
        }
        auditoria.registrar(u.getId(), AuditoriaContaService.EMAIL_TROCA_CONFIRMADA,
                "e-mail da conta alterado; demais sessoes encerradas", ip);

        String nome = u.getNome();
        String quando = QUANDO.format(LocalDateTime.now(clock));
        AposCommit.executar("aviso de e-mail alterado", () -> emailSender.enviar(emailAntigo,
                textos.get("perfil.email.alterado-antigo.assunto"),
                textos.get("perfil.email.alterado-antigo.corpo", nome, quando)));
    }

    private static IllegalArgumentException linkInvalido() {
        return new IllegalArgumentException("Este link é inválido ou expirou. Faça um novo pedido de troca de e-mail.");
    }

    private static String normalizar(String bruto) {
        String email = bruto == null ? "" : bruto.trim().toLowerCase();
        if (email.length() > 150 || !FORMATO.matcher(email).matches()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        return email;
    }
}
