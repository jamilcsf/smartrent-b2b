package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.dto.CalendarioDtos.ImovelResumo;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Confirmacao;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Conversa;
import br.com.unisenai.smartrent.dto.SmartChatDtos.DenunciaPedido;
import br.com.unisenai.smartrent.dto.SmartChatDtos.EnvioResposta;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Interlocutor;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Mensagem;
import br.com.unisenai.smartrent.dto.SmartChatDtos.Perfil;
import br.com.unisenai.smartrent.dto.SmartChatDtos.ReservaResumo;
import br.com.unisenai.smartrent.model.BloqueioUsuario;
import br.com.unisenai.smartrent.model.DenunciaChat;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.SmartChatConversa;
import br.com.unisenai.smartrent.model.SmartChatMensagem;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.TipoMensagem;
import br.com.unisenai.smartrent.repository.BloqueioUsuarioRepository;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.SmartChatConversaRepository;
import br.com.unisenai.smartrent.repository.SmartChatMensagemRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.MessageFilterService.Resultado;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.EmailNaoVerificadoException;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SmartChat: canal unico entre cliente e gestor. Quem participa de uma conversa
 * e conferido aqui, no servidor, em toda operacao (cliente so ve as proprias
 * conversas; gestor so as dos proprios imoveis).
 *
 * <p>O que sai para o navegador, para notificacoes e para a lista de conversas e
 * sempre o texto ja filtrado; o original fica so no banco, para a futura
 * moderacao. Mensagens de sistema sao isentas do filtro, imutaveis e idempotentes
 * (chave unica): confirmar ou cancelar duas vezes, retry e concorrencia nao
 * duplicam nada, e uma falha aqui nunca desfaz a reserva.
 */
@Service
public class SmartChatService {

    private static final Logger log = LoggerFactory.getLogger(SmartChatService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final Set<String> MOTIVOS_DENUNCIA = Set.of("ASSEDIO_OFENSAS", "SPAM", "TENTATIVA_DE_GOLPE",
            "CONTEUDO_IMPROPRIO", "CONTATO_EXTERNO", "OUTRO");

    private final SmartChatConversaRepository conversas;
    private final SmartChatMensagemRepository mensagens;
    private final ImovelRepository imoveis;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final DenunciaChatRepository denuncias;
    private final BloqueioUsuarioRepository bloqueios;
    private final MessageFilterService filtro;
    private final ChatEventos eventos;
    private final NotificacaoService notificacoes;
    private final NotificadorEmail email;
    private final TextosPoliticas textos;
    private final LimitadorDeTaxa limitador;
    private final AnaliseComportamentoChat analise;
    private final ChatProperties props;
    private final Clock clock;

    /** Ultima notificacao de mensagem por conversa e destinatario: agrupa para nao gerar um aviso por mensagem. */
    private final Map<String, Instant> ultimaNotificacao = new ConcurrentHashMap<>();

    public SmartChatService(SmartChatConversaRepository conversas, SmartChatMensagemRepository mensagens,
                            ImovelRepository imoveis, ReservaRepository reservas, UsuarioRepository usuarios,
                            DenunciaChatRepository denuncias, BloqueioUsuarioRepository bloqueios,
                            MessageFilterService filtro, ChatEventos eventos, NotificacaoService notificacoes,
                            NotificadorEmail email, TextosPoliticas textos, LimitadorDeTaxa limitador,
                            AnaliseComportamentoChat analise, ChatProperties props, Clock clock) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.imoveis = imoveis;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.denuncias = denuncias;
        this.bloqueios = bloqueios;
        this.filtro = filtro;
        this.eventos = eventos;
        this.notificacoes = notificacoes;
        this.email = email;
        this.textos = textos;
        this.limitador = limitador;
        this.analise = analise;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------- abrir conversa

    /** Botao de chat do anuncio: so enquanto o anuncio esta publicado e so para cliente logado. */
    @Transactional
    public Conversa abrirPorImovel(Usuario cliente, Long imovelId) {
        if (cliente == null || cliente.getPapel() == PapelUsuario.ANFITRIAO) {
            throw new AcessoNegadoException("O gestor responde às conversas iniciadas pelos clientes.");
        }
        Imovel imovel = imoveis.findByIdParaAtualizar(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
        if (!AnuncioGestorMapper.noCatalogo(imovel, Agora.de(clock))) {
            throw new TransicaoInvalidaException("Este anúncio não está publicado no momento.");
        }
        if (imovel.getUsuario().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Você não pode conversar consigo mesmo.");
        }
        exigirEmailVerificadoParaIniciar(cliente, cliente, imovel);
        SmartChatConversa c = obter(cliente, imovel);
        liberarAba(cliente);
        return resposta(c, cliente);
    }

    /** Botao da pagina da reserva e do calendario: funciona sempre, mesmo com o anuncio fora do ar. */
    @Transactional
    public Conversa abrirPorReserva(Usuario usuario, Long reservaId) {
        Reserva r = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
        boolean ehCliente = r.getCliente() != null && r.getCliente().getId().equals(usuario.getId());
        boolean ehGestor = r.getImovel().getUsuario().getId().equals(usuario.getId());
        if (!ehCliente && !ehGestor) {
            throw new AcessoNegadoException("Esta reserva não é sua.");
        }
        if (r.getCliente() == null) {
            throw new TransicaoInvalidaException("Este hóspede não tem conta na plataforma, então não há conversa no SmartChat.");
        }
        exigirEmailVerificadoParaIniciar(usuario, r.getCliente(), r.getImovel());
        SmartChatConversa c = obter(r.getCliente(), r.getImovel());
        c.getReservaIds().add(r.getId());
        if (ehCliente) {
            liberarAba(usuario);
        }
        return resposta(c, usuario);
    }

    /**
     * Abrir uma conversa que ainda nao existe exige e-mail verificado de quem abre. A que ja existe continua
     * abrindo (leitura). A criacao automatica depois da reserva ({@link #garantirConversaDaReserva}) nao passa
     * por aqui e segue funcionando.
     */
    private void exigirEmailVerificadoParaIniciar(Usuario ator, Usuario cliente, Imovel imovel) {
        if (ator.isEmailVerificado()) {
            return;
        }
        boolean existe = conversas.findByClienteIdAndGestorIdAndImovelId(cliente.getId(),
                imovel.getUsuario().getId(), imovel.getId()).isPresent();
        if (!existe) {
            throw new EmailNaoVerificadoException();
        }
    }

    /** Procura ou cria, serializando pelo imovel (travado), para nunca criar duas conversas iguais. */
    private SmartChatConversa obter(Usuario cliente, Imovel imovel) {
        Imovel travado = imoveis.findByIdParaAtualizar(imovel.getId()).orElse(imovel);
        Usuario gestor = travado.getUsuario();
        return conversas.findByClienteIdAndGestorIdAndImovelId(cliente.getId(), gestor.getId(), travado.getId())
                .orElseGet(() -> {
                    SmartChatConversa c = new SmartChatConversa();
                    c.setCliente(cliente);
                    c.setGestor(gestor);
                    c.setImovel(travado);
                    c.setCriadaEm(clock.instant());
                    return conversas.save(c);
                });
    }

    private void liberarAba(Usuario cliente) {
        if (cliente != null && cliente.getPapel() != PapelUsuario.ANFITRIAO && !cliente.isSmartchatLiberado()) {
            cliente.setSmartchatLiberado(true);
            usuarios.save(cliente);
        }
    }

    // ------------------------------------------- criacao a partir da reserva

    /**
     * Garante a conversa da reserva confirmada: reutiliza a existente, liga a reserva,
     * libera a aba do cliente e registra a mensagem de sistema UMA unica vez. Quem
     * chama depois do commit da reserva o faz em transacao propria (ver
     * {@link ChatReservaListener}): o erro daqui nunca desfaz a confirmacao.
     */
    @Transactional
    public void garantirConversaDaReserva(Long reservaId) {
        Reserva r = reservas.findById(reservaId).orElse(null);
        if (r == null || r.getCliente() == null) {
            return;
        }
        SmartChatConversa c = obter(r.getCliente(), r.getImovel());
        c.getReservaIds().add(r.getId());
        liberarAba(r.getCliente());

        String texto = "Reserva confirmada. Use este chat para tratar detalhes com o gestor.\n"
                + CodigoImovel.de(r.getImovel().getId()) + " · " + r.getImovelTituloSnapshot() + "\n"
                + "Check-in: " + r.getDataCheckin().format(DATA) + " · Check-out: " + r.getDataCheckout().format(DATA)
                + " · " + r.getNumeroHospedes() + (r.getNumeroHospedes() == 1 ? " hóspede" : " hóspedes");
        boolean nova = gravarSistema(c, "reserva-confirmada:" + r.getId(), texto);
        if (nova) {
            String link = "/smartchat.html?conversa=" + c.getId();
            avisar(r.getCliente(), r.getImovel().getId(), "Reserva confirmada", "Seu chat com o gestor está pronto. " + texto.split("\n")[1]
                    + ". A política de cancelamento da sua reserva está em /reserva.html?id=" + r.getId()
                    + " (texto provisório).", link);
            avisar(c.getGestor(), r.getImovel().getId(), "Nova reserva confirmada",
                    r.getHospedeNome() + " reservou " + r.getImovelTituloSnapshot() + " (" + r.getDataCheckin().format(DATA) + " a "
                            + r.getDataCheckout().format(DATA) + "). Há uma conversa aberta.", link);
        }
    }

    /** Mensagem de sistema do cancelamento (quem cancelou e o resultado do reembolso), idempotente. */
    @Transactional
    public void registrarCancelamento(Long reservaId) {
        Reserva r = reservas.findById(reservaId).orElse(null);
        if (r == null || r.getCliente() == null || !r.getStatus().cancelada()) {
            return;
        }
        SmartChatConversa c = obter(r.getCliente(), r.getImovel());
        c.getReservaIds().add(r.getId());
        String quem = switch (String.valueOf(r.getCanceladaPor())) {
            case "CLIENTE" -> "pelo cliente";
            case "GESTOR" -> "pelo gestor";
            default -> "pelo sistema";
        };
        String resultado = switch (String.valueOf(r.getCancelamentoRegra())) {
            case "GESTOR_CANCELA", "REEMBOLSO_INTEGRAL_ANTECEDENCIA" -> "Reserva cancelada com reembolso integral.";
            case "SEM_REEMBOLSO_PRAZO" -> "Reserva cancelada sem reembolso, conforme a política.";
            default -> textos.get("cancelamento.pendente");
        };
        String texto = "Reserva de " + r.getDataCheckin().format(DATA) + " a " + r.getDataCheckout().format(DATA)
                + " cancelada " + quem + ". " + resultado;
        gravarSistema(c, "reserva-cancelada:" + r.getId(), texto);
    }

    /** Grava a mensagem de sistema se a chave ainda nao existe. Devolve se criou. Sistema nao passa pelo filtro. */
    private boolean gravarSistema(SmartChatConversa c, String chave, String texto) {
        if (mensagens.existsByChaveIdempotencia(chave)) {
            return false;
        }
        Instant agora = clock.instant();
        SmartChatMensagem m = new SmartChatMensagem();
        m.setConversa(c);
        m.setTipo(TipoMensagem.SISTEMA);
        m.setTextoFiltrado(texto);
        m.setChaveIdempotencia(chave);
        m.setCriadaEm(agora);
        m.setLidaEm(agora); // aviso da plataforma: nao conta como "nao lida"
        mensagens.save(m);
        c.setUltimaMensagemEm(agora);
        conversas.save(c);
        avisarNovidade(c, null, m.getId());
        return true;
    }

    // --------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<Conversa> listar(Usuario usuario, Long imovelId) {
        return conversas.findDoUsuario(usuario.getId(), imovelId).stream().map(c -> resposta(c, usuario)).toList();
    }

    @Transactional(readOnly = true)
    public Conversa buscar(Usuario usuario, Long id) {
        return resposta(participante(usuario, id), usuario);
    }

    @Transactional(readOnly = true)
    public List<Mensagem> listarMensagens(Usuario usuario, Long conversaId, Long depoisDe) {
        SmartChatConversa c = participante(usuario, conversaId);
        return mensagens.findDepoisDe(c.getId(), depoisDe == null ? 0L : depoisDe, PageRequest.of(0, 200)).stream()
                .map(m -> mensagem(m, usuario)).toList();
    }

    @Transactional(readOnly = true)
    public long naoLidas(Usuario usuario) {
        return mensagens.contarNaoLidasDoUsuario(usuario.getId());
    }

    @Transactional(readOnly = true)
    public Perfil perfil(Usuario usuario, Long conversaId) {
        SmartChatConversa c = participante(usuario, conversaId);
        return new Perfil(interlocutor(c, usuario), imovelResumo(c.getImovel()), reservaResumo(c));
    }

    // --------------------------------------------------------------- escrita

    @Transactional
    public EnvioResposta enviar(Usuario usuario, Long conversaId, String texto) {
        SmartChatConversa c = participante(usuario, conversaId);
        if (!usuario.isEmailVerificado()) { // regra no backend; o front so mostra o motivo
            throw new EmailNaoVerificadoException();
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escreva uma mensagem.");
        }
        if (texto.length() > props.maxCaracteres()) {
            throw new IllegalArgumentException("A mensagem pode ter no máximo " + props.maxCaracteres() + " caracteres.");
        }
        // Depois de um alerta de envio em massa o limite fica mais restrito por um tempo (a conta nao e suspensa).
        int limite = analise.limiteRestrito(usuario.getId())
                ? Math.min(props.mensagensPorMinuto(), analise.limiteRestritoPorMinuto()) : props.mensagensPorMinuto();
        if (!limitador.permitir("chat:" + usuario.getId(), limite, Duration.ofMinutes(1))) {
            throw new LimiteExcedidoException("Você está enviando mensagens rápido demais. Aguarde um instante.");
        }
        exigirSemBloqueio(c);

        String limpo = Sanitizador.texto(texto);
        Resultado resultado = filtro.moderarTexto(limpo); // sempre no servidor
        Instant agora = clock.instant();

        SmartChatMensagem m = new SmartChatMensagem();
        m.setConversa(c);
        m.setAutor(usuario);
        m.setTipo(TipoMensagem.NORMAL);
        m.setTextoFiltrado(resultado.texto());
        m.setTextoOriginal(limpo.length() > 2000 ? limpo.substring(0, 2000) : limpo); // restrito ao backend
        m.setOcorrencias(resultado.ocorrencias());
        String resumo = analise.resumoDe(limpo);
        m.setTextoHmac(resumo);
        m.setCategorias(resultado.categorias().stream().map(Enum::name).collect(Collectors.joining(",")));
        m.setCriadaEm(agora);
        mensagens.save(m);
        analise.aposEnvio(usuario, resumo, resultado.suspeitaFraude());

        c.setUltimaMensagemEm(agora);
        conversas.save(c);
        if (c.getCliente().getId().equals(usuario.getId())) {
            liberarAba(usuario);
        }

        Usuario destino = c.outroLado(usuario);
        avisarNovidade(c, usuario, m.getId());
        notificarDestinatario(c, usuario, destino, resultado.texto());

        String aviso = resultado.alterado()
                ? "Alguns trechos foram ocultados por conterem telefone, link externo ou conteúdo impróprio. Use o SmartChat para tratar tudo sobre a reserva."
                : null;
        return new EnvioResposta(mensagem(m, usuario), aviso);
    }

    @Transactional
    public void marcarLidas(Usuario usuario, Long conversaId) {
        SmartChatConversa c = participante(usuario, conversaId);
        int n = mensagens.marcarLidas(c.getId(), usuario.getId(), clock.instant());
        if (n > 0) {
            eventos.publicar(c.outroLado(usuario).getId(), "lida", Map.of("conversaId", c.getId()));
        }
    }

    // ------------------------------------------- denunciar e bloquear (prototipo)

    /**
     * Registra a denuncia para o futuro modulo de administracao. Sem efeito
     * funcional: nada e bloqueado nem avisado ao denunciado.
     */
    @Transactional
    public Confirmacao denunciar(Usuario usuario, Long conversaId, DenunciaPedido p) {
        SmartChatConversa c = participante(usuario, conversaId);
        Usuario denunciado = c.outroLado(usuario);
        if (denunciado.getId().equals(usuario.getId())) {
            throw new IllegalArgumentException("Não é possível denunciar a si mesmo.");
        }
        if (p == null || p.motivo() == null || !MOTIVOS_DENUNCIA.contains(p.motivo())) {
            throw new IllegalArgumentException("Escolha o motivo da denúncia.");
        }
        Instant agora = clock.instant();
        if (denuncias.existsByConversaIdAndDenuncianteIdAndMotivoAndCriadaEmAfter(c.getId(), usuario.getId(), p.motivo(),
                agora.minusSeconds(props.denunciaIntervaloSeg()))) {
            throw new LimiteExcedidoException("Você já registrou uma denúncia igual há pouco tempo.");
        }
        DenunciaChat d = new DenunciaChat();
        d.setConversa(c);
        d.setDenunciante(usuario);
        d.setDenunciado(denunciado);
        d.setMotivo(p.motivo());
        String descricao = p.descricao() == null ? null : Sanitizador.texto(p.descricao());
        d.setDescricao(descricao == null || descricao.isBlank() ? null
                : (descricao.length() > 1000 ? descricao.substring(0, 1000) : descricao));
        if (p.mensagensIds() != null && !p.mensagensIds().isEmpty()) {
            List<Long> validas = mensagens.findByConversaIdAndIdIn(c.getId(), p.mensagensIds()).stream()
                    .map(SmartChatMensagem::getId).toList();
            d.setMensagensAnexadas(validas.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        d.setStatus("PENDENTE");
        d.setCriadaEm(agora);
        denuncias.save(d);
        return new Confirmacao("Denúncia registrada.");
    }

    /**
     * Registra o pedido de bloqueio. PROTOTIPO: persiste, mas o chat continua
     * funcionando enquanto {@code smartrent.chat.bloqueio-ativo=false}. O ponto unico
     * de aplicacao futura e {@link #exigirSemBloqueio(SmartChatConversa)}.
     */
    @Transactional
    public Confirmacao bloquear(Usuario usuario, Long conversaId) {
        SmartChatConversa c = participante(usuario, conversaId);
        Usuario bloqueado = c.outroLado(usuario);
        Instant agora = clock.instant();
        if (bloqueios.existsByBloqueadorIdAndBloqueadoIdAndCriadoEmAfter(usuario.getId(), bloqueado.getId(),
                agora.minusSeconds(props.denunciaIntervaloSeg()))) {
            throw new LimiteExcedidoException("Você já registrou esta solicitação há pouco tempo.");
        }
        BloqueioUsuario b = new BloqueioUsuario();
        b.setConversa(c);
        b.setBloqueador(usuario);
        b.setBloqueado(bloqueado);
        b.setStatus("REGISTRADO");
        b.setCriadoEm(agora);
        bloqueios.save(b);
        return new Confirmacao("Sua solicitação de bloqueio foi registrada.");
    }

    /**
     * PONTO UNICO do bloqueio de usuario. Com {@code SMARTCHAT_BLOCK_ENFORCEMENT=false}
     * (padrao) nao faz nada: o chat funciona normalmente. Quando o modulo de
     * administracao existir, e aqui que o bloqueio passara a valer.
     */
    private void exigirSemBloqueio(SmartChatConversa c) {
        if (!props.bloqueioAtivo()) {
            return;
        }
        // Aplicacao futura: consultar bloqueios ativos entre c.getCliente() e c.getGestor() e recusar o envio.
    }

    // ----------------------------------------------------------------- apoio

    private SmartChatConversa participante(Usuario usuario, Long id) {
        SmartChatConversa c = conversas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa não encontrada."));
        if (!c.participa(usuario)) {
            throw new AcessoNegadoException("Você não participa desta conversa.");
        }
        return c;
    }

    private Conversa resposta(SmartChatConversa c, Usuario usuario) {
        SmartChatMensagem ultima = mensagens.findFirstByConversaIdOrderByIdDesc(c.getId()).orElse(null);
        String previa = ultima == null ? null : abreviar(MessageFilterService.semMarcadores(ultima.getTextoFiltrado()), 80);
        boolean publicado = AnuncioGestorMapper.noCatalogo(c.getImovel(), Agora.de(clock));
        return new Conversa(c.getId(), imovelResumo(c.getImovel()), interlocutor(c, usuario), previa,
                c.getUltimaMensagemEm(), mensagens.contarNaoLidas(c.getId(), usuario.getId()), reservaResumo(c), publicado,
                c.getCliente().getId().equals(usuario.getId()) ? "CLIENTE" : "GESTOR");
    }

    private static ImovelResumo imovelResumo(Imovel i) {
        return new ImovelResumo(i.getId(), CodigoImovel.de(i.getId()), i.getTitulo());
    }

    private static Interlocutor interlocutor(SmartChatConversa c, Usuario usuario) {
        Usuario outro = c.outroLado(usuario);
        String[] partes = outro.getNome().trim().split("\\s+");
        String iniciais = (partes[0].substring(0, 1) + (partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "")).toUpperCase();
        return new Interlocutor(outro.getNome(), iniciais, c.getGestor().getId().equals(outro.getId()) ? "Gestor" : "Cliente",
                br.com.unisenai.smartrent.dto.UsuarioResponse.fotoUrl(outro), outro.isEmailVerificado());
    }

    /** Reserva exibida no cabecalho: a ativa mais proxima, senao a mais recente do cliente naquele imovel. */
    private ReservaResumo reservaResumo(SmartChatConversa c) {
        List<Reserva> lista = reservas.findByClienteIdAndImovelIdOrderByDataCheckinDesc(c.getCliente().getId(), c.getImovel().getId());
        Reserva r = lista.stream().filter(x -> !x.getStatus().cancelada()).findFirst().orElse(lista.isEmpty() ? null : lista.get(0));
        return r == null ? null : new ReservaResumo(r.getId(), r.getStatus(), r.getDataCheckin(), r.getDataCheckout(), r.getNumeroHospedes());
    }

    private static Mensagem mensagem(SmartChatMensagem m, Usuario usuario) {
        boolean sistema = m.getTipo() == TipoMensagem.SISTEMA;
        return new Mensagem(m.getId(), m.getTipo().name(), !sistema && m.getAutor().getId().equals(usuario.getId()),
                sistema ? "SmartRent" : m.getAutor().getNome(), m.getTextoFiltrado(), m.getOcorrencias(), m.getCriadaEm(),
                m.getLidaEm() != null, !sistema && !m.getAutor().getId().equals(usuario.getId()) && sinalizada(m));
    }

    private static boolean sinalizada(SmartChatMensagem m) {
        return m.getCategorias() != null && m.getCategorias().contains(MessageFilterService.Categoria.SUSPEITA_FRAUDE.name());
    }

    private static String abreviar(String t, int max) {
        if (t == null) {
            return null;
        }
        String s = t.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void avisarNovidade(SmartChatConversa c, Usuario autor, Long mensagemId) {
        for (Usuario u : List.of(c.getCliente(), c.getGestor())) {
            if (autor != null && u.getId().equals(autor.getId())) {
                continue;
            }
            eventos.publicar(u.getId(), "mensagem", Map.of("conversaId", c.getId(), "mensagemId", mensagemId));
        }
    }

    /** Aviso fora do chat: so se o destinatario nao esta conectado e no maximo um por conversa a cada N minutos. */
    private void notificarDestinatario(SmartChatConversa c, Usuario autor, Usuario destino, String textoFiltrado) {
        if (eventos.conectado(destino.getId())) {
            return;
        }
        String chave = c.getId() + ":" + destino.getId();
        Instant agora = clock.instant();
        Instant ultima = ultimaNotificacao.get(chave);
        if (ultima != null && ultima.isAfter(agora.minus(Duration.ofMinutes(props.notificacaoAgrupamentoMin())))) {
            return;
        }
        ultimaNotificacao.put(chave, agora);
        // sempre o texto filtrado: o borrao vira "[ocultado]"
        String previa = abreviar(MessageFilterService.semMarcadores(textoFiltrado), 80);
        // O e-mail (hoje so log) nao leva o texto: texto de mensagem nunca vai a log (ADR-006).
        avisar(destino, c.getImovel().getId(), "Nova mensagem de " + autor.getNome(), previa,
                "Você recebeu uma nova mensagem no SmartChat.", "/smartchat.html?conversa=" + c.getId());
    }

    private void avisar(Usuario destino, Long imovelId, String titulo, String mensagem, String link) {
        avisar(destino, imovelId, titulo, mensagem, mensagem, link);
    }

    private void avisar(Usuario destino, Long imovelId, String titulo, String mensagem, String corpoEmail, String link) {
        try {
            notificacoes.criar(destino, imovelId, titulo, mensagem, link);
            email.enviar(destino, titulo, corpoEmail);
        } catch (RuntimeException e) {
            log.warn("Falha ao avisar {} sobre o SmartChat", destino.getId(), e);
        }
    }
}
