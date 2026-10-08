package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatSegurancaProperties;
import br.com.unisenai.smartrent.dto.AdminDtos.Pagina;
import br.com.unisenai.smartrent.dto.AdminDtos.UsuarioAdmin;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.AcaoItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.AlertaItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.ComunicadoEnviado;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.ContaSuspensa;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.Decisao;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisaoDenunciaRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisoesAutomaticas;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DenunciaDetalhe;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DenunciaItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MensagemAdminRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MensagemEvidencia;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.NivelRemetenteItem;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.ResumoFiltro;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.RevisaoAlertaRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.UsuarioResumo;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;
import br.com.unisenai.smartrent.model.AcaoModeracao;
import br.com.unisenai.smartrent.model.AlertaInterno;
import br.com.unisenai.smartrent.model.Comunicado;
import br.com.unisenai.smartrent.model.DenunciaChat;
import br.com.unisenai.smartrent.model.SmartChatMensagem;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.NivelRemetente;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAlertaInterno;
import br.com.unisenai.smartrent.model.enums.TipoAcaoModeracao;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import br.com.unisenai.smartrent.repository.AcaoModeracaoRepository;
import br.com.unisenai.smartrent.repository.AlertaInternoRepository;
import br.com.unisenai.smartrent.repository.ComunicadoRepository;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.SmartChatMensagemRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Area de moderacao do painel de admin (ADR-010): denuncias, decisoes automaticas, suspensao e reativacao de contas
 * e avisos da administracao. A autorizacao (papel ADMIN) e do SecurityConfig; aqui ficam as regras do que um admin pode
 * fazer. Toda acao grava uma linha imutavel em {@code moderacao_acoes} (e, quando mexe numa conta, na auditoria da conta).
 * Evidencias de denuncia: so as mensagens que a pessoa anexou (texto como o destinatario viu, com trechos ocultados), e
 * abrir o detalhe fica registrado. As decisoes automaticas so mostram contagens, nunca texto de mensagem.
 */
@Service
public class ModeracaoService {

    private static final Logger LOG = LoggerFactory.getLogger(ModeracaoService.class);

    static final int TAMANHO_MAX = 50;
    static final int NOTA_MIN = 5;
    static final int MENSAGENS_POR_HORA = 30;
    private static final Set<TipoAcaoModeracao> DECISOES_DENUNCIA =
            Set.of(TipoAcaoModeracao.DENUNCIA_PROCEDENTE, TipoAcaoModeracao.DENUNCIA_IMPROCEDENTE);
    private static final Set<TipoAcaoModeracao> DECISOES_ALERTA =
            Set.of(TipoAcaoModeracao.ALERTA_REVISADO, TipoAcaoModeracao.ALERTA_DESCARTADO);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final UsuarioRepository usuarios;
    private final DenunciaChatRepository denuncias;
    private final AlertaInternoRepository alertas;
    private final SmartChatMensagemRepository mensagens;
    private final AcaoModeracaoRepository acoes;
    private final ComunicadoRepository comunicados;
    private final AuditoriaContaService auditoria;
    private final EmailSender email;
    private final ChatSegurancaProperties chat;
    private final LimitadorDeTaxa limitador;
    private final Clock clock;

    public ModeracaoService(UsuarioRepository usuarios, DenunciaChatRepository denuncias, AlertaInternoRepository alertas,
                            SmartChatMensagemRepository mensagens, AcaoModeracaoRepository acoes,
                            ComunicadoRepository comunicados, AuditoriaContaService auditoria, EmailSender email,
                            ChatSegurancaProperties chat, LimitadorDeTaxa limitador, Clock clock) {
        this.usuarios = usuarios;
        this.denuncias = denuncias;
        this.alertas = alertas;
        this.mensagens = mensagens;
        this.acoes = acoes;
        this.comunicados = comunicados;
        this.auditoria = auditoria;
        this.email = email;
        this.chat = chat;
        this.limitador = limitador;
        this.clock = clock;
    }

    // =============================================================== Denuncias

    @Transactional(readOnly = true)
    public Pagina<DenunciaItem> denuncias(String status, Integer pagina, Integer tamanho) {
        Pageable p = pagina(pagina, tamanho);
        String filtro = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        Page<DenunciaChat> page = filtro == null ? denuncias.findAllByOrderByIdDesc(p) : denuncias.findByStatusOrderByIdDesc(filtro, p);
        return paginar(page, d -> item(d, 140));
    }

    /** Abre o detalhe (e registra que este admin leu as evidencias). */
    @Transactional
    public DenunciaDetalhe denuncia(Usuario admin, Long id) {
        DenunciaChat d = denuncia(id);
        acoes.save(new AcaoModeracao(d.getDenunciado(), admin, TipoAcaoModeracao.DENUNCIA_ABERTA, null, d.getId(), null, clock.instant()));
        List<MensagemEvidencia> evidencias = new ArrayList<>();
        if (d.getMensagensAnexadas() != null && !d.getMensagensAnexadas().isBlank()) {
            List<Long> ids = Arrays.stream(d.getMensagensAnexadas().split(",")).map(String::trim).filter(x -> !x.isEmpty())
                    .map(Long::valueOf).toList();
            for (SmartChatMensagem m : mensagens.findByConversaIdAndIdIn(d.getConversa().getId(), ids)) {
                evidencias.add(new MensagemEvidencia(m.getCodigoPublico(), m.getAutor() == null ? "Plataforma" : m.getAutor().getNome(),
                        m.getCriadaEm(), MessageFilterService.semMarcadores(m.getTextoFiltrado())));
            }
            evidencias.sort((a, b) -> a.criadaEm().compareTo(b.criadaEm()));
        }
        Usuario alvo = d.getDenunciado();
        List<AlertaItem> alertasDoAlvo = alertas.findTop10ByUsuarioIdOrderByIdDesc(alvo.getId()).stream().map(this::item).toList();
        List<AcaoItem> historico = acoes.findTop20ByUsuarioIdOrderByIdDesc(alvo.getId()).stream()
                .filter(a -> a.getTipo() != TipoAcaoModeracao.DENUNCIA_ABERTA).map(this::item).toList();
        return new DenunciaDetalhe(item(d, 1000), d.getConversa().getImovel().getTitulo(), evidencias,
                denuncias.countByDenunciadoIdAndStatus(alvo.getId(), "PROCEDENTE"), alertasDoAlvo, historico,
                acoes.findFirstByDenunciaIdAndTipoInOrderByIdDesc(d.getId(), DECISOES_DENUNCIA).map(this::decisao).orElse(null));
    }

    @Transactional
    public DenunciaItem decidirDenuncia(Usuario admin, Long id, DecisaoDenunciaRequest req, String ip) {
        DenunciaChat d = denuncia(id);
        if (req == null) {
            throw new IllegalArgumentException("Informe a decisão.");
        }
        if (!"PENDENTE".equals(d.getStatus())) {
            throw new TransicaoInvalidaException("Esta denúncia já foi decidida.");
        }
        boolean procedente = "PROCEDENTE".equalsIgnoreCase(req.decisao());
        if (!procedente && !"IMPROCEDENTE".equalsIgnoreCase(req.decisao())) {
            throw new IllegalArgumentException("A decisão deve ser PROCEDENTE ou IMPROCEDENTE.");
        }
        String nota = nota(req.nota(), "Registre a justificativa da decisão (mínimo de " + NOTA_MIN + " caracteres).");
        if (req.suspenderDenunciado() && !procedente) {
            throw new IllegalArgumentException("Só é possível suspender a conta quando a denúncia é procedente.");
        }
        Instant agora = clock.instant();
        d.setStatus(procedente ? "PROCEDENTE" : "IMPROCEDENTE");
        denuncias.save(d);
        acoes.save(new AcaoModeracao(d.getDenunciado(), admin,
                procedente ? TipoAcaoModeracao.DENUNCIA_PROCEDENTE : TipoAcaoModeracao.DENUNCIA_IMPROCEDENTE, nota, d.getId(), null, agora));
        String motivo = rotuloMotivo(d.getMotivo());
        if (req.suspenderDenunciado()) {
            suspender(admin, d.getDenunciado().getId(), "Denúncia #" + d.getId() + " procedente (" + motivo + "): " + nota, ip, d.getId());
        }
        if (req.avisarDenunciante()) {
            String texto = procedente
                    ? "Analisamos a denúncia que você registrou no SmartChat (motivo: " + motivo + ") e tomamos as providências cabíveis. Obrigado por ajudar a manter a plataforma segura."
                    : "Analisamos a denúncia que você registrou no SmartChat (motivo: " + motivo + ") e não identificamos violação das regras da plataforma neste caso. Se tiver novos fatos, registre uma nova denúncia.";
            entregar(admin, d.getDenunciante(), NivelRemetente.MODERACAO, "Sua denúncia foi analisada", texto, d.getId(), true);
        }
        if (req.avisarDenunciado() && procedente) {
            entregar(admin, d.getDenunciado(), NivelRemetente.MODERACAO, "Aviso da moderação sobre sua conduta no SmartChat",
                    "Recebemos uma denúncia sobre mensagens suas no SmartChat (motivo: " + motivo + ") e, após análise, ela foi considerada procedente. "
                            + "Pedimos que siga as regras de uso da plataforma; condutas repetidas podem levar à suspensão da conta. "
                            + "Se discordar, responda por este canal de suporte.", d.getId(), true);
        }
        return item(d, 140);
    }

    // =============================================================== Decisoes automaticas

    @Transactional(readOnly = true)
    public DecisoesAutomaticas decisoesAutomaticas(String status, Integer dias, Integer pagina, Integer tamanho) {
        Pageable p = pagina(pagina, tamanho);
        StatusAlertaInterno filtro = null;
        if (status != null && !status.isBlank()) {
            try {
                filtro = StatusAlertaInterno.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Situação de alerta inválida.");
            }
        }
        Page<AlertaInterno> page = filtro == null ? alertas.findAllByOrderByIdDesc(p) : alertas.findByStatusOrderByIdDesc(filtro, p);
        List<AlertaItem> lista = page.getContent().stream().map(this::item).toList();
        long limitesAtivos = lista.stream().filter(AlertaItem::efeitoAtivo).count();
        return new DecisoesAutomaticas(lista, alertas.countByStatus(StatusAlertaInterno.ABERTO), limitesAtivos, resumoFiltro(dias));
    }

    private ResumoFiltro resumoFiltro(Integer diasPedidos) {
        int dias = diasPedidos == null ? 30 : Math.max(1, Math.min(diasPedidos, 365));
        Instant desde = clock.instant().minus(Duration.ofDays(dias));
        List<ContagemRotulo> categorias = new ArrayList<>();
        for (MessageFilterService.Categoria c : MessageFilterService.Categoria.values()) {
            categorias.add(new ContagemRotulo(c.rotulo, mensagens.contarComCategoriaDesde(desde, "%" + c.name() + "%")));
        }
        return new ResumoFiltro(dias, mensagens.contarNormaisDesde(desde), mensagens.contarFiltradasDesde(desde), categorias,
                mensagens.filtradasPorDia(desde), mensagens.autoresMaisFiltrados(desde, PageRequest.of(0, 8)));
    }

    @Transactional
    public AlertaItem revisarAlerta(Usuario admin, Long id, RevisaoAlertaRequest req) {
        AlertaInterno a = alertas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Alerta não encontrado."));
        if (req == null) {
            throw new IllegalArgumentException("Informe a decisão.");
        }
        if (a.getStatus() != StatusAlertaInterno.ABERTO) {
            throw new TransicaoInvalidaException("Este alerta já foi revisado.");
        }
        boolean descartar = "DESCARTADO".equalsIgnoreCase(req.decisao());
        if (!descartar && !"REVISADO".equalsIgnoreCase(req.decisao())) {
            throw new IllegalArgumentException("A decisão deve ser REVISADO ou DESCARTADO.");
        }
        String nota = nota(req.nota(), "Registre a justificativa (mínimo de " + NOTA_MIN + " caracteres).");
        a.setStatus(descartar ? StatusAlertaInterno.DESCARTADO : StatusAlertaInterno.REVISADO);
        alertas.save(a);
        acoes.save(new AcaoModeracao(a.getUsuario(), admin, descartar ? TipoAcaoModeracao.ALERTA_DESCARTADO : TipoAcaoModeracao.ALERTA_REVISADO,
                nota, null, a.getId(), clock.instant()));
        return item(a);
    }

    // =============================================================== Contas

    @Transactional
    public UsuarioAdmin suspender(Usuario admin, Long alvoId, String motivo, String ip) {
        return suspender(admin, alvoId, motivo, ip, null);
    }

    private UsuarioAdmin suspender(Usuario admin, Long alvoId, String motivoBruto, String ip, Long denunciaId) {
        Usuario alvo = alvoModeravel(admin, alvoId);
        String motivo = nota(motivoBruto, "Informe o motivo da suspensão (mínimo de " + NOTA_MIN + " caracteres).");
        if (!alvo.isAtivo()) {
            throw new TransicaoInvalidaException("A conta já está suspensa.");
        }
        alvo.setAtivo(false);
        usuarios.save(alvo);
        acoes.save(new AcaoModeracao(alvo, admin, TipoAcaoModeracao.SUSPENSAO, motivo, denunciaId, null, clock.instant()));
        auditoria.registrar(alvo.getId(), "ADMIN_CONTA_DESATIVADA", "Por administrador #" + admin.getId(), ip);
        entregar(admin, alvo, NivelRemetente.MODERACAO, "Sua conta foi suspensa",
                "Sua conta foi suspensa pela administração da plataforma. Motivo: " + motivo
                        + "\n\nEnquanto durar a suspensão você não consegue entrar, e os seus anúncios (se for anfitrião) saem do catálogo. "
                        + "Para contestar ou pedir a reativação, responda este e-mail ou fale com o suporte.", denunciaId, true);
        return resumoAdmin(alvo);
    }

    @Transactional
    public UsuarioAdmin reativar(Usuario admin, Long alvoId, String motivoBruto, String ip) {
        Usuario alvo = alvoModeravel(admin, alvoId);
        String motivo = nota(motivoBruto, "Informe o motivo da reativação (mínimo de " + NOTA_MIN + " caracteres).");
        if (alvo.isAtivo()) {
            throw new TransicaoInvalidaException("A conta já está ativa.");
        }
        alvo.setAtivo(true);
        usuarios.save(alvo);
        acoes.save(new AcaoModeracao(alvo, admin, TipoAcaoModeracao.REATIVACAO, motivo, null, null, clock.instant()));
        auditoria.registrar(alvo.getId(), "ADMIN_CONTA_ATIVADA", "Por administrador #" + admin.getId(), ip);
        entregar(admin, alvo, NivelRemetente.MODERACAO, "Sua conta foi reativada",
                "Sua conta foi reativada pela administração da plataforma e você já pode entrar novamente. Obs.: " + motivo, null, true);
        return resumoAdmin(alvo);
    }

    /** O admin nao mexe na propria conta nem na de outro admin (evita se trancar fora e escalada lateral). */
    private Usuario alvoModeravel(Usuario admin, Long alvoId) {
        Usuario alvo = usuarios.findById(alvoId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (alvo.getId().equals(admin.getId())) {
            throw new AcessoNegadoException("Você não pode alterar a própria conta por aqui.");
        }
        if (alvo.getPapel() == PapelUsuario.ADMIN) {
            throw new AcessoNegadoException("Contas de administrador não podem ser alteradas por aqui.");
        }
        return alvo;
    }

    @Transactional(readOnly = true)
    public Pagina<ContaSuspensa> contasSuspensas(Integer pagina, Integer tamanho) {
        return paginar(usuarios.findByAtivoFalseOrderByIdDesc(pagina(pagina, tamanho)), u -> {
            AcaoModeracao a = acoes.findFirstByUsuarioIdAndTipoOrderByIdDesc(u.getId(), TipoAcaoModeracao.SUSPENSAO).orElse(null);
            return new ContaSuspensa(resumo(u), a == null ? null : a.getMotivo(), a == null ? null : a.getAdmin().getNome(),
                    a == null ? null : a.getCriadoEm());
        });
    }

    @Transactional(readOnly = true)
    public Pagina<AcaoItem> historico(Long usuarioId, Integer pagina, Integer tamanho) {
        Pageable p = pagina(pagina, tamanho);
        return paginar(usuarioId == null ? acoes.findAllByOrderByIdDesc(p) : acoes.findByUsuarioIdOrderByIdDesc(usuarioId, p), this::item);
    }

    // =============================================================== Avisos

    public List<NivelRemetenteItem> niveis() {
        return Arrays.stream(NivelRemetente.values()).map(n -> new NivelRemetenteItem(n.name(), n.rotulo)).toList();
    }

    @Transactional
    public ComunicadoEnviado enviarMensagem(Usuario admin, Long destinoId, MensagemAdminRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Escreva a mensagem.");
        }
        Usuario destino = usuarios.findById(destinoId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (destino.getId().equals(admin.getId())) {
            throw new IllegalArgumentException("Escolha outro destinatário.");
        }
        NivelRemetente nivel;
        try {
            nivel = req.nivel() == null || req.nivel().isBlank() ? NivelRemetente.ADMINISTRACAO
                    : NivelRemetente.valueOf(req.nivel().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Nível de remetente inválido.");
        }
        String assunto = Sanitizador.linha(req.assunto());
        String texto = Sanitizador.texto(req.texto());
        if (assunto == null || assunto.length() < 3) {
            throw new IllegalArgumentException("Informe o assunto (mínimo de 3 caracteres).");
        }
        if (assunto.length() > 150) {
            throw new IllegalArgumentException("O assunto pode ter no máximo 150 caracteres.");
        }
        if (texto == null || texto.length() < 5) {
            throw new IllegalArgumentException("Escreva a mensagem (mínimo de 5 caracteres).");
        }
        if (texto.length() > 2000) {
            throw new IllegalArgumentException("A mensagem pode ter no máximo 2000 caracteres.");
        }
        if (!limitador.permitir("moderacao-msg:" + admin.getId(), MENSAGENS_POR_HORA, Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitas mensagens enviadas na última hora. Aguarde um pouco.");
        }
        Comunicado c = entregar(admin, destino, nivel, assunto, texto, null, req.copiarPorEmail());
        acoes.save(new AcaoModeracao(destino, admin, TipoAcaoModeracao.MENSAGEM, assunto, null, null, c.getCriadoEm()));
        return item(c);
    }

    @Transactional(readOnly = true)
    public Pagina<ComunicadoEnviado> comunicadosEnviados(Integer pagina, Integer tamanho) {
        return paginar(comunicados.findAllByOrderByIdDesc(pagina(pagina, tamanho)), this::item);
    }

    /** Grava o aviso na caixa do usuario e, se pedido, manda copia por e-mail (falha de e-mail nunca desfaz a acao). */
    private Comunicado entregar(Usuario admin, Usuario destino, NivelRemetente nivel, String assunto, String texto,
                                Long denunciaId, boolean copiaPorEmail) {
        Comunicado c = comunicados.save(new Comunicado(destino, admin, nivel, assunto, texto, denunciaId, clock.instant()));
        if (copiaPorEmail && destino.getEmail() != null) {
            try {
                email.enviar(destino.getEmail(), "[SmartRent] " + assunto, nivel.rotulo + "\n\n" + texto
                        + "\n\nVocê também pode ler este aviso em \"Avisos\" depois de entrar na plataforma.");
            } catch (RuntimeException e) {
                LOG.warn("Cópia por e-mail do aviso {} não enviada ({}).", c.getId(), e.getClass().getSimpleName());
            }
        }
        return c;
    }

    // =============================================================== Mapeamento

    private DenunciaChat denuncia(Long id) {
        return denuncias.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Denúncia não encontrada."));
    }

    private DenunciaItem item(DenunciaChat d, int limiteDescricao) {
        String desc = d.getDescricao();
        if (desc != null && desc.length() > limiteDescricao) {
            desc = desc.substring(0, limiteDescricao) + "…";
        }
        return new DenunciaItem(d.getId(), d.getMotivo(), d.getStatus(), d.getCriadaEm(), resumo(d.getDenunciante()),
                resumo(d.getDenunciado()), desc, denuncias.countByDenunciadoId(d.getDenunciado().getId()));
    }

    private AlertaItem item(AlertaInterno a) {
        Instant fim = a.getCriadoEm().plus(Duration.ofMinutes(chat.limiteRestritoMinutos()));
        boolean massa = a.getTipo() == TipoAlertaInterno.ENVIO_EM_MASSA;
        boolean ativo = massa && a.getStatus() != StatusAlertaInterno.DESCARTADO && clock.instant().isBefore(fim);
        String efeito;
        if (!massa) {
            efeito = "Apenas sinaliza para a equipe: nenhuma restrição automática.";
        } else if (a.getStatus() == StatusAlertaInterno.DESCARTADO) {
            efeito = "Restrição de envio removida por um admin (falso positivo).";
        } else if (ativo) {
            efeito = "Limite de envio reduzido a " + chat.limiteRestritoPorMinuto() + " mensagens/min até "
                    + HORA.format(fim.atZone(clock.getZone())) + ".";
        } else {
            efeito = "Limite de envio foi reduzido por " + chat.limiteRestritoMinutos() + " min (já encerrado).";
        }
        String explicacao = massa
                ? "Mesmo texto enviado a " + a.getContagem() + " conversas diferentes em poucos minutos."
                : a.getContagem() + " mensagem(ns) citando pagamento ou contato fora da plataforma.";
        return new AlertaItem(a.getId(), a.getTipo().name(), explicacao, a.getContagem(), a.getJanelaInicio(), a.getJanelaFim(),
                a.getStatus().name(), a.getCriadoEm(), resumo(a.getUsuario()), efeito, ativo,
                acoes.findFirstByAlertaIdAndTipoInOrderByIdDesc(a.getId(), DECISOES_ALERTA).map(this::decisao).orElse(null));
    }

    private AcaoItem item(AcaoModeracao a) {
        return new AcaoItem(a.getId(), a.getTipo().name(), a.getMotivo(), resumo(a.getUsuario()), a.getAdmin().getNome(),
                a.getDenunciaId(), a.getAlertaId(), a.getCriadoEm());
    }

    private ComunicadoEnviado item(Comunicado c) {
        return new ComunicadoEnviado(c.getId(), resumo(c.getDestinatario()), c.getRemetenteNivel().rotulo, c.getAssunto(),
                c.getTexto(), c.getRemetenteAdmin().getNome(), c.getCriadoEm(), c.getLidoEm());
    }

    private Decisao decisao(AcaoModeracao a) {
        return new Decisao(a.getTipo().name(), a.getMotivo(), a.getAdmin().getNome(), a.getCriadoEm());
    }

    static UsuarioResumo resumo(Usuario u) {
        return new UsuarioResumo(u.getId(), u.getNome(), u.getEmail(), u.getPapel().name(), u.isAtivo());
    }

    private static UsuarioAdmin resumoAdmin(Usuario u) {
        return new UsuarioAdmin(u.getId(), u.getNome(), u.getEmail(), u.getPapel().name(), u.isAtivo(), u.isEmailVerificado(),
                u.getDataCriacao());
    }

    private static String rotuloMotivo(String motivo) {
        return switch (motivo == null ? "" : motivo) {
            case "ASSEDIO_OFENSAS" -> "assédio ou ofensas";
            case "SPAM" -> "spam";
            case "TENTATIVA_DE_GOLPE" -> "tentativa de golpe";
            case "CONTEUDO_IMPROPRIO" -> "conteúdo impróprio";
            case "CONTATO_EXTERNO" -> "contato fora da plataforma";
            case "OUTRO" -> "outro motivo";
            default -> motivo == null ? "outro" : motivo.toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String nota(String bruta, String mensagem) {
        String n = Sanitizador.linha(bruta);
        if (n == null || n.length() < NOTA_MIN) {
            throw new IllegalArgumentException(mensagem);
        }
        return n.length() > 400 ? n.substring(0, 400) : n;
    }

    private static Pageable pagina(Integer pagina, Integer tamanho) {
        return PageRequest.of(pagina == null ? 0 : Math.max(0, pagina), tamanho == null ? 15 : Math.max(1, Math.min(tamanho, TAMANHO_MAX)));
    }

    private static <E, T> Pagina<T> paginar(Page<E> pagina, Function<E, T> mapa) {
        return new Pagina<>(pagina.getContent().stream().map(mapa).toList(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
