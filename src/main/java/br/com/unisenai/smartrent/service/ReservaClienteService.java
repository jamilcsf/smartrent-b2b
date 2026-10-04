package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Pedido;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Politica;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Previa;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Resposta;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Pagamento;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.OrigemReserva;
import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.PagamentoRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException;
import br.com.unisenai.smartrent.service.erro.PagamentoRecusadoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reserva feita pelo proprio cliente (autoatendimento): previa com valores e
 * politica, criacao pendente, pagamento pelo gateway e listagem. Tudo que depende
 * de data usa o relogio e o fuso da plataforma (Brasilia), nao os do navegador.
 */
@Service
public class ReservaClienteService {

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ReservaService reservaService;
    private final GatewayPagamento gateway;
    private final ReservaClienteMapper mapper;
    private final AuditoriaService auditoria;
    private final PoliticaCancelamentoProperties props;
    private final TextosPoliticas textos;
    private final ApplicationEventPublisher publicador;
    private final Clock clock;
    private final ZoneId zona;

    private final AccountRestrictionService restricoes;

    public ReservaClienteService(ReservaRepository reservaRepository, ImovelRepository imovelRepository,
                                 PagamentoRepository pagamentoRepository, ReservaService reservaService,
                                 GatewayPagamento gateway, ReservaClienteMapper mapper, AuditoriaService auditoria,
                                 PoliticaCancelamentoProperties props, TextosPoliticas textos,
                                 ApplicationEventPublisher publicador, Clock clock, ZoneId zonaDaPlataforma,
                                 AccountRestrictionService restricoes) {
        this.restricoes = restricoes;
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.reservaService = reservaService;
        this.gateway = gateway;
        this.mapper = mapper;
        this.auditoria = auditoria;
        this.props = props;
        this.textos = textos;
        this.publicador = publicador;
        this.clock = clock;
        this.zona = zonaDaPlataforma;
    }

    // ------------------------------------------------------------------ previa

    /** Valores, regras do imovel e politica, antes de qualquer cobranca ou gravacao. */
    @Transactional(readOnly = true)
    public Previa previa(Usuario cliente, Pedido p) {
        Imovel imovel = imovelVisivel(p.imovelId());
        exigirReservavel(cliente, imovel);
        Previa previa = montarPrevia(imovel, p);
        reservaService.exigirDisponivel(imovel.getId(), p.dataCheckin(), p.dataCheckout(), null);
        return previa;
    }

    // ----------------------------------------------------------------- criacao

    @Transactional
    public Resposta criar(Usuario cliente, Pedido p) {
        if (p.imovelId() == null) {
            throw new IllegalArgumentException("Informe o imóvel da reserva.");
        }
        Imovel imovel = imovelRepository.findByIdParaAtualizar(p.imovelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
        exigirReservavel(cliente, imovel);
        Previa previa = montarPrevia(imovel, p);

        if (previa.politica().semDireitoAReembolso() && !Boolean.TRUE.equals(p.cienteSemReembolso())) {
            throw new ConflitoComDetalhesException("CONFIRMAR_SEM_REEMBOLSO",
                    textos.get("reserva.aviso-sem-reembolso", props.antecedenciaHoras()), List.of());
        }

        Reserva r = new Reserva();
        r.setImovel(imovel);
        r.setCliente(cliente);
        r.setHospedeNome(Sanitizador.linha(cliente.getNome()));
        r.setHospedeEmail(Sanitizador.linha(cliente.getEmail()));
        r.setDataCheckin(p.dataCheckin());
        r.setDataCheckout(p.dataCheckout());
        r.setNumeroHospedes(previa.numeroHospedes());
        r.setStatus(StatusReserva.PENDENTE);
        r.setDataCriacao(Agora.de(clock));
        r.setOrigem(OrigemReserva.DIRETA);
        r.setObservacoes(p.observacoes() == null ? null : Sanitizador.texto(p.observacoes()));
        ReservaService.gravarSnapshot(r, imovel, props);
        reservaService.cadastrarReserva(r); // conflito de reserva ou bloqueio: mesma regra do gestor

        auditoria.acao(imovel, cliente.getId(), "RESERVA_CRIADA",
                "reserva=" + r.getId() + " " + r.getDataCheckin() + " a " + r.getDataCheckout()
                        + " total=" + r.getTotalSnapshot() + " politica=" + r.getPoliticaVersao());
        return mapper.resposta(r);
    }

    // --------------------------------------------------------------- pagamento

    /**
     * Cobra a reserva pendente. Idempotente: pagar de novo uma reserva ja
     * confirmada devolve o mesmo resultado, e a chave de idempotencia (unica no
     * banco) impede cobranca dupla mesmo com cliques simultaneos (a linha da
     * reserva fica travada). Recusa nao desfaz o registro da tentativa.
     */
    @Transactional(noRollbackFor = PagamentoRecusadoException.class)
    public Resposta pagar(Usuario cliente, Long id, String token) {
        Reserva r = reservaRepository.findByIdParaAtualizar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
        if (r.getCliente() == null || !r.getCliente().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Esta reserva não é sua.");
        }
        if (r.getStatus() == StatusReserva.CONFIRMADA || r.getStatus() == StatusReserva.CONCLUIDA) {
            return mapper.resposta(r); // ja paga: nada a cobrar de novo
        }
        if (r.getStatus() != StatusReserva.PENDENTE) {
            throw new TransicaoInvalidaException("Esta reserva não pode mais ser paga (situação: " + r.getStatus() + ").");
        }
        if (mapper.expirada(r)) {
            throw new TransicaoInvalidaException("O prazo para pagar esta reserva expirou. Faça uma nova reserva.");
        }
        // Pagar confirma uma reserva NOVA (a pendente ainda nao vale): vale a mesma restricao de criar.
        restricoes.exigirPodeReservar(cliente);
        restricoes.exigirAceitaNovasReservas(r.getImovel().getUsuario() == null ? null : r.getImovel().getUsuario().getId());

        long tentativa = pagamentoRepository.countByReservaId(r.getId()) + 1;
        String chave = "reserva:" + r.getId() + ":cobranca:" + tentativa;
        GatewayPagamento.Resultado resultado = gateway.cobrar(chave, r.getTotalSnapshot(), r.getMoeda(), token);

        Pagamento pg = new Pagamento();
        pg.setReserva(r);
        pg.setValor(r.getTotalSnapshot());
        pg.setMoeda(r.getMoeda());
        pg.setChaveIdempotencia(chave);
        pg.setCriadoEm(clock.instant());
        if (!resultado.aprovado()) {
            pg.setStatus(StatusPagamento.RECUSADO);
            pg.setMotivoRecusa(resultado.motivo());
            pagamentoRepository.save(pg);
            throw new PagamentoRecusadoException(resultado.motivo() == null ? "Pagamento recusado." : resultado.motivo());
        }
        pg.setStatus(StatusPagamento.APROVADO);
        pg.setRefGateway(resultado.referencia());
        pagamentoRepository.save(pg);

        r.setStatus(StatusReserva.CONFIRMADA);
        reservaRepository.save(r);
        auditoria.acao(r.getImovel(), cliente.getId(), "RESERVA_PAGA",
                "reserva=" + r.getId() + " valor=" + r.getTotalSnapshot() + " ref=" + resultado.referencia());
        publicador.publishEvent(new ReservaConfirmadaEvent(r.getId()));
        return mapper.resposta(r);
    }

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<Resposta> listar(Usuario cliente) {
        return reservaRepository.findByClienteIdOrderByDataCheckinDesc(cliente.getId()).stream()
                .map(mapper::resposta).toList();
    }

    @Transactional(readOnly = true)
    public Resposta buscar(Usuario cliente, Long id) {
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva não encontrada."));
        if (r.getCliente() == null || !r.getCliente().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Esta reserva não é sua.");
        }
        return mapper.resposta(r);
    }

    // ------------------------------------------------------------------ apoio

    private Imovel imovelVisivel(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Informe o imóvel da reserva.");
        }
        return imovelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
    }

    private void exigirReservavel(Usuario cliente, Imovel imovel) {
        if (imovel.getUsuario() != null && imovel.getUsuario().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Você não pode reservar o seu próprio imóvel.");
        }
        if (!AnuncioGestorMapper.noCatalogo(imovel, Agora.de(clock))) {
            throw new TransicaoInvalidaException("Este imóvel não está disponível para reservas no momento.");
        }
        restricoes.exigirPodeReservar(cliente);
        restricoes.exigirAceitaNovasReservas(imovel.getUsuario() == null ? null : imovel.getUsuario().getId());
        if (imovel.getValorDiariaBase() == null) {
            throw new IllegalArgumentException("O imóvel não tem valor de diária definido.");
        }
    }

    private Previa montarPrevia(Imovel imovel, Pedido p) {
        LocalDate checkin = p.dataCheckin();
        LocalDate checkout = p.dataCheckout();
        if (checkin == null || checkout == null) {
            throw new IllegalArgumentException("Informe as datas de check-in e check-out.");
        }
        if (!checkout.isAfter(checkin)) {
            throw new IllegalArgumentException("A data de check-out deve ser posterior à data de check-in.");
        }
        if (checkin.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A data de check-in não pode estar no passado.");
        }
        int hospedes = p.numeroHospedes() == null ? 1 : p.numeroHospedes();
        ReservaService.validarRegrasDoImovel(imovel.getMinimoDiarias(), imovel.getCapacidadeHospedes(),
                checkin, checkout, hospedes);

        long noites = ChronoUnit.DAYS.between(checkin, checkout);
        BigDecimal diaria = imovel.getValorDiariaBase();
        BigDecimal subtotal = diaria.multiply(BigDecimal.valueOf(noites));
        BigDecimal taxa = imovel.getTaxaLimpeza() == null ? BigDecimal.ZERO : imovel.getTaxaLimpeza();

        Instant limite = PrazoReembolso.limite(checkin, props.checkinHora(), props.antecedenciaHoras(), zona);
        boolean semDireito = !PrazoReembolso.dentroDoPrazo(clock.instant(), limite);
        Politica politica = new Politica(props.versao(), props.antecedenciaHoras(), limite, semDireito,
                props.checkinHora().toString(), zona.getId(), ReservaClienteMapper.TEXTO_POLITICA_URL);
        return new Previa(imovel.getId(), CodigoImovel.de(imovel.getId()), imovel.getTitulo(), checkin, checkout,
                (int) noites, hospedes, imovel.getMinimoDiarias(), imovel.getCapacidadeHospedes(), diaria,
                subtotal, taxa, subtotal.add(taxa), "BRL", politica);
    }
}
