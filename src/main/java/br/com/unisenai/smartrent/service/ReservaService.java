package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.dto.ReservaRequest;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.BloqueioDataRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Reservas do gestor.
 *
 * <p><b>Integridade de preco (anti-fraude).</b> No instante da criacao a
 * reserva grava um snapshot imutavel: valor de cada diaria, numero de diarias,
 * taxas, total, moeda e os dados do imovel (titulo, endereco, caracteristicas).
 * Dai em diante, mudar o preco ou editar o anuncio nao muda o que foi
 * reservado. O total vem sempre deste snapshot; o {@code valorTotal} enviado
 * pelo cliente HTTP e ignorado, e nenhum endpoint altera as colunas do
 * snapshot (elas sao {@code updatable=false} na entidade).
 *
 * <p>Ao alterar as <em>datas</em> de uma reserva existente, vale o preco de
 * diaria do snapshot original, nunca o preco atual do imovel.
 */
@Service
public class ReservaService {

    static final String MSG_CONFLITO = "Conflito de datas detectado para este imovel.";
    /** Mesma mensagem para o cliente, qualquer que seja a origem: o bloqueio manual nunca e revelado. */
    static final String MSG_INDISPONIVEL = "Estas datas estão indisponíveis para este imóvel.";

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final ImovelAcesso acesso;
    private final Clock clock;
    private final PoliticaCancelamentoProperties politica;
    private final BloqueioDataRepository bloqueioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher publicador;

    public ReservaService(ReservaRepository reservaRepository,
                          ImovelRepository imovelRepository,
                          ImovelAcesso acesso,
                          Clock clock,
                          PoliticaCancelamentoProperties politica,
                          BloqueioDataRepository bloqueioRepository,
                          UsuarioRepository usuarioRepository,
                          ApplicationEventPublisher publicador) {
        this.politica = politica;
        this.bloqueioRepository = bloqueioRepository;
        this.usuarioRepository = usuarioRepository;
        this.publicador = publicador;
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.acesso = acesso;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<Reserva> listar(Usuario gestor) {
        acesso.exigirGestor(gestor);
        return gestor.getPapel() == PapelUsuario.ADMIN
                ? reservaRepository.findAll()
                : reservaRepository.findByImovelUsuarioIdOrderByDataCheckinDesc(gestor.getId());
    }

    @Transactional(readOnly = true)
    public Reserva buscar(Usuario gestor, Long id) {
        return doGestor(gestor, id);
    }

    // ----------------------------------------------------------------- criacao

    /**
     * Cria a reserva de um imovel do gestor, ja com o snapshot. So aceita
     * imovel que esteja no catalogo: fora do ar (em edicao, aguardando
     * republicacao ou em pre-publicacao) ele nao recebe novas reservas.
     */
    @Transactional
    public Reserva criar(Usuario gestor, ReservaRequest req) {
        acesso.exigirGestor(gestor);
        if (req.imovelId() == null) {
            throw new IllegalArgumentException("Informe o imovel da reserva.");
        }
        validarPeriodo(req.dataCheckin(), req.dataCheckout());
        validarHospede(req);

        // Trava o imovel: reserva e bloqueio simultaneos nas mesmas datas nao passam juntos.
        Imovel imovel = imovelRepository.findByIdParaAtualizar(req.imovelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imovel nao encontrado: " + req.imovelId()));
        acesso.conferir(gestor, imovel);

        LocalDateTime agora = Agora.de(clock);
        if (!AnuncioGestorMapper.noCatalogo(imovel, agora)) {
            throw new TransicaoInvalidaException(
                    "Este imovel esta fora do ar e nao recebe novas reservas no momento (situacao: "
                            + imovel.getStatus() + ").");
        }
        if (imovel.getValorDiariaBase() == null) {
            throw new IllegalArgumentException("O imovel nao tem valor de diaria definido.");
        }

        Reserva reserva = new Reserva();
        reserva.setImovel(imovel);
        copiarDadosDoHospede(req, reserva);
        reserva.setDataCheckin(req.dataCheckin());
        reserva.setDataCheckout(req.dataCheckout());
        reserva.setStatus(req.status() == null ? StatusReserva.CONFIRMADA : req.status());
        if (reserva.getStatus().cancelada() || reserva.getStatus() == StatusReserva.CONCLUIDA) {
            throw new IllegalArgumentException("Uma reserva nova deve nascer pendente ou confirmada.");
        }
        reserva.setDataCriacao(Agora.de(clock));
        reserva.setCliente(clienteDoEmail(reserva.getHospedeEmail()));
        reserva.setOrigem(req.origem() == null ? OrigemReserva.DIRETA : req.origem());
        reserva.setObservacoes(req.observacoes());
        reserva.setNumeroHospedes(req.numeroHospedes() == null ? 1 : req.numeroHospedes());
        validarRegrasDoImovel(imovel.getMinimoDiarias(), imovel.getCapacidadeHospedes(),
                reserva.getDataCheckin(), reserva.getDataCheckout(), reserva.getNumeroHospedes());
        gravarSnapshot(reserva, imovel, politica);
        Reserva salva = cadastrarReserva(reserva);
        if (salva.getStatus() == StatusReserva.CONFIRMADA) {
            publicador.publishEvent(new ReservaConfirmadaEvent(salva.getId()));
        }
        return salva;
    }

    /** Vincula a reserva ao usuario CLIENTE de mesmo e-mail, quando existe (habilita o SmartChat). */
    private Usuario clienteDoEmail(String email) {
        if (email == null) {
            return null;
        }
        return usuarioRepository.findByEmail(email.trim())
                .or(() -> usuarioRepository.findByEmail(email.trim().toLowerCase()))
                .filter(u -> u.getPapel() == PapelUsuario.CLIENTE)
                .orElse(null);
    }

    /**
     * Grava a reserva apos checar choque de datas.
     *
     * <p>A checagem e a gravacao correm na mesma transacao; o travamento
     * otimista da entidade (campo versao) cobre a janela entre elas.
     */
    @Transactional
    public Reserva cadastrarReserva(Reserva reserva) {
        exigirDisponivel(reserva.getImovel().getId(), reserva.getDataCheckin(), reserva.getDataCheckout(), null);
        return reservaRepository.save(reserva);
    }

    /**
     * Verifica reservas ativas e bloqueios manuais no periodo. Quem chama para
     * gravar ja travou a linha do imovel, entao o resultado vale ate o commit.
     */
    @Transactional(readOnly = true)
    public void exigirDisponivel(Long imovelId, LocalDate checkin, LocalDate checkout, Long reservaIgnorada) {
        boolean conflito = reservaIgnorada == null
                ? reservaRepository.existeConflitoDeDatas(imovelId, checkin, checkout)
                : reservaRepository.existeConflitoDeDatasExceto(imovelId, reservaIgnorada, checkin, checkout);
        if (conflito) {
            throw new IllegalArgumentException(MSG_CONFLITO);
        }
        if (bloqueioRepository.existeBloqueio(imovelId, checkin, checkout)) {
            throw new IllegalArgumentException(MSG_INDISPONIVEL);
        }
    }

    /** Congela preco e dados do imovel na reserva. Chamado uma unica vez, na criacao. */
    static void gravarSnapshot(Reserva reserva, Imovel imovel, PoliticaCancelamentoProperties politica) {
        BigDecimal diaria = imovel.getValorDiariaBase();
        List<LocalDate> noites = new ArrayList<>();
        for (LocalDate d = reserva.getDataCheckin(); d.isBefore(reserva.getDataCheckout()); d = d.plusDays(1)) {
            noites.add(d);
        }
        BigDecimal soma = BigDecimal.ZERO;
        for (LocalDate noite : noites) {
            reserva.getPrecosDiarios().put(noite, diaria); // hoje o preco e o mesmo em toda data
            soma = soma.add(diaria);
        }
        BigDecimal taxaLimpeza = imovel.getTaxaLimpeza() == null ? BigDecimal.ZERO : imovel.getTaxaLimpeza();
        BigDecimal taxas = taxaLimpeza; // a taxa de limpeza e cobrada uma vez por reserva
        BigDecimal total = soma.add(taxas);

        reserva.setMoeda("BRL");
        reserva.setNumeroDiarias(noites.size());
        reserva.setPrecoDiariaSnapshot(diaria);
        reserva.setTaxasSnapshot(taxas);
        reserva.setTotalSnapshot(total);
        reserva.setValorTotal(total);
        reserva.setMinimoDiariasSnapshot(imovel.getMinimoDiarias());
        reserva.setTaxaLimpezaSnapshot(taxaLimpeza);
        reserva.setLimiteHospedesSnapshot(imovel.getCapacidadeHospedes());
        reserva.setPoliticaAntecedenciaHoras(politica.antecedenciaHoras());
        reserva.setPoliticaRegretDias(politica.regretDias());
        reserva.setPoliticaVersao(politica.versao());
        reserva.setImovelTituloSnapshot(imovel.getTitulo());
        reserva.setImovelEnderecoSnapshot(descreverEndereco(imovel));
        reserva.setImovelCaracteristicasSnapshot(descreverCaracteristicas(imovel));
    }

    // -------------------------------------------------------------- atualizacao

    /**
     * Atualiza hospede, datas, status, origem e observacoes. O imovel nao muda
     * e o snapshot nunca e tocado; se as datas mudarem, o total e recalculado
     * com a diaria do snapshot.
     */
    @Transactional
    public Reserva atualizar(Usuario gestor, Long id, ReservaRequest req) {
        Reserva reserva = doGestor(gestor, id);
        if (req.imovelId() != null && !req.imovelId().equals(reserva.getImovel().getId())) {
            throw new IllegalArgumentException("Nao e possivel trocar o imovel de uma reserva existente.");
        }
        if (reserva.getStatus().cancelada()) {
            throw new TransicaoInvalidaException("Uma reserva cancelada nao pode ser alterada.");
        }
        if (req.status() != null && req.status().cancelada()) {
            throw new IllegalArgumentException("Para cancelar use a acao Cancelar, que exige o motivo e aplica a politica de reembolso.");
        }
        validarPeriodo(req.dataCheckin(), req.dataCheckout());
        validarHospede(req);
        imovelRepository.findByIdParaAtualizar(reserva.getImovel().getId()); // serializa com bloqueios e novas reservas

        boolean datasMudaram = !req.dataCheckin().equals(reserva.getDataCheckin())
                || !req.dataCheckout().equals(reserva.getDataCheckout());
        StatusReserva statusAnterior = reserva.getStatus();
        exigirDisponivel(reserva.getImovel().getId(), req.dataCheckin(), req.dataCheckout(), reserva.getId());

        int hospedes = req.numeroHospedes() == null ? reserva.getNumeroHospedes() : req.numeroHospedes();
        // Vale o que foi combinado na criacao (snapshot), nao o que o anuncio diz hoje.
        validarRegrasDoImovel(reserva.getMinimoDiariasSnapshot(), reserva.getLimiteHospedesSnapshot(),
                req.dataCheckin(), req.dataCheckout(), hospedes);

        copiarDadosDoHospede(req, reserva);
        reserva.setNumeroHospedes(hospedes);
        reserva.setDataCheckin(req.dataCheckin());
        reserva.setDataCheckout(req.dataCheckout());
        reserva.setObservacoes(req.observacoes());
        if (req.status() != null) {
            reserva.setStatus(req.status());
        }
        if (req.origem() != null) {
            reserva.setOrigem(req.origem());
        }
        if (datasMudaram) {
            long diarias = ChronoUnit.DAYS.between(req.dataCheckin(), req.dataCheckout());
            reserva.setValorTotal(reserva.getPrecoDiariaSnapshot()
                    .multiply(BigDecimal.valueOf(diarias))
                    .add(reserva.getTaxasSnapshot()));
        }
        Reserva salva = reservaRepository.save(reserva);
        if (statusAnterior != StatusReserva.CONFIRMADA && salva.getStatus() == StatusReserva.CONFIRMADA) {
            publicador.publishEvent(new ReservaConfirmadaEvent(salva.getId()));
        }
        return salva;
    }

    @Transactional
    public void excluir(Usuario gestor, Long id) {
        Reserva reserva = doGestor(gestor, id);
        if (reserva.getCliente() != null) {
            throw new TransicaoInvalidaException(
                    "Reservas de clientes nao podem ser excluidas (ha historico de pagamento e conversa). Use Cancelar.");
        }
        reservaRepository.delete(reserva);
    }

    // ------------------------------------------------------------------- apoio

    private Reserva doGestor(Usuario gestor, Long id) {
        acesso.exigirGestor(gestor);
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva nao encontrada."));
        try {
            acesso.conferir(gestor, reserva.getImovel());
        } catch (AcessoNegadoException e) {
            throw new AcessoNegadoException("Voce nao tem permissao sobre esta reserva.");
        }
        return reserva;
    }

    private static void validarPeriodo(LocalDate checkin, LocalDate checkout) {
        if (checkin == null || checkout == null) {
            throw new IllegalArgumentException("Informe as datas de check-in e check-out.");
        }
        // Regra de negocio: a data final deve ser posterior a inicial.
        if (!checkout.isAfter(checkin)) {
            throw new IllegalArgumentException("A data de check-out deve ser posterior a data de check-in.");
        }
    }

    /** Minimo de diarias e limite de hospedes: o servidor valida, independentemente do front. */
    static void validarRegrasDoImovel(int minimoDiarias, int limiteHospedes,
                                      LocalDate checkin, LocalDate checkout, int hospedes) {
        long noites = ChronoUnit.DAYS.between(checkin, checkout);
        if (noites < minimoDiarias) {
            throw new IllegalArgumentException("Este imóvel exige no mínimo " + minimoDiarias
                    + (minimoDiarias == 1 ? " diária" : " diárias") + " por reserva (foram solicitadas " + noites + ").");
        }
        if (hospedes < 1) {
            throw new IllegalArgumentException("Informe ao menos 1 hóspede.");
        }
        if (hospedes > limiteHospedes) {
            throw new IllegalArgumentException("Este imóvel aceita no máximo " + limiteHospedes
                    + (limiteHospedes == 1 ? " hóspede" : " hóspedes") + " (foram informados " + hospedes + ").");
        }
    }

    private static void validarHospede(ReservaRequest req) {
        if (req.hospedeNome() == null || req.hospedeNome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do hospede.");
        }
        if (req.hospedeEmail() == null || req.hospedeEmail().isBlank()) {
            throw new IllegalArgumentException("Informe o e-mail do hospede.");
        }
    }

    private static void copiarDadosDoHospede(ReservaRequest req, Reserva reserva) {
        reserva.setHospedeNome(Sanitizador.linha(req.hospedeNome()));
        reserva.setHospedeEmail(Sanitizador.linha(req.hospedeEmail()));
        reserva.setHospedeTelefone(Sanitizador.linha(req.hospedeTelefone()));
    }

    static String descreverEndereco(Imovel i) {
        var e = i.getEndereco();
        if (e == null) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        partes.add(e.getLogradouro());
        if (e.getNumero() != null && !e.getNumero().isBlank()) {
            partes.add(e.getNumero());
        }
        partes.add(e.getBairro());
        partes.add(e.getCidade() + "/" + e.getEstado());
        String texto = String.join(", ", partes.stream().filter(p -> p != null && !p.isBlank()).toList());
        return texto.length() > 400 ? texto.substring(0, 400) : texto;
    }

    static String descreverCaracteristicas(Imovel i) {
        List<String> partes = new ArrayList<>();
        if (i.getMetragemQuadrada() != null) {
            partes.add(i.getMetragemQuadrada() + " m²");
        }
        partes.add(i.getNumeroQuartos() + " quarto(s)");
        partes.add(i.getNumeroBanheiros() + " banheiro(s)");
        if (i.getVagasGaragem() != null && i.getVagasGaragem() > 0) {
            partes.add(i.getVagasGaragem() + " vaga(s)");
        }
        partes.add("até " + i.getCapacidadeHospedes() + " hóspedes");
        String texto = String.join(" · ", partes);
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
