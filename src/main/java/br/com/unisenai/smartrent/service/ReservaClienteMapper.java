package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PoliticaCancelamentoProperties;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Cancelamento;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Politica;
import br.com.unisenai.smartrent.dto.ReservaClienteDtos.Resposta;
import br.com.unisenai.smartrent.model.Reembolso;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.enums.StatusReembolso;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.ReembolsoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Monta a reserva como o cliente a ve, com a politica do snapshot e o resultado do cancelamento. */
@Component
public class ReservaClienteMapper {

    public static final String TEXTO_POLITICA_URL = "/api/politica-cancelamento";

    private final ReembolsoRepository reembolsoRepository;
    private final RefundPolicyService politicaService;
    private final PoliticaCancelamentoProperties props;
    private final TextosPoliticas textos;
    private final long expiraMinutos;
    private final Clock clock;
    private final ZoneId zona;

    public ReservaClienteMapper(ReembolsoRepository reembolsoRepository, RefundPolicyService politicaService,
                                PoliticaCancelamentoProperties props, TextosPoliticas textos,
                                br.com.unisenai.smartrent.config.ReservaProperties reservaProps,
                                Clock clock, ZoneId zonaDaPlataforma) {
        this.reembolsoRepository = reembolsoRepository;
        this.politicaService = politicaService;
        this.props = props;
        this.textos = textos;
        this.expiraMinutos = reservaProps.pendenteExpiraMinutos();
        this.clock = clock;
        this.zona = zonaDaPlataforma;
    }

    /** Quando uma reserva autoatendida pendente deixa de segurar as datas. */
    public LocalDateTime expiraEm(Reserva r) {
        if (r.getStatus() != StatusReserva.PENDENTE || r.getCliente() == null) {
            return null;
        }
        return Agora.somar(r.getDataCriacao(), Duration.ofMinutes(expiraMinutos), clock);
    }

    public boolean expirada(Reserva r) {
        LocalDateTime expira = expiraEm(r);
        return expira != null && !Agora.de(clock).isBefore(expira);
    }

    public Politica politica(Reserva r) {
        Instant limite = politicaService.limiteDoReembolso(r);
        return new Politica(r.getPoliticaVersao(), r.getPoliticaAntecedenciaHoras(), limite,
                !PrazoReembolso.dentroDoPrazo(clock.instant(), limite),
                props.checkinHora().toString(), zona.getId(), ReservaClienteMapper.TEXTO_POLITICA_URL);
    }

    public Resposta resposta(Reserva r) {
        boolean pendente = r.getStatus() == StatusReserva.PENDENTE;
        boolean ativa = pendente || r.getStatus() == StatusReserva.CONFIRMADA;
        return new Resposta(
                r.getId(),
                r.getImovel().getId(),
                CodigoImovel.de(r.getImovel().getId()),
                r.getImovelTituloSnapshot(),
                r.getImovelEnderecoSnapshot(),
                r.getDataCheckin(),
                r.getDataCheckout(),
                r.getNumeroDiarias(),
                r.getNumeroHospedes(),
                r.getPrecoDiariaSnapshot(),
                r.getTaxaLimpezaSnapshot(),
                r.getTotalSnapshot(),
                r.getMoeda(),
                r.getStatus(),
                r.getDataCriacao(),
                expiraEm(r),
                pendente && !expirada(r),
                ativa,
                politica(r),
                r.getStatus().cancelada() ? cancelamento(r) : null);
    }

    private Cancelamento cancelamento(Reserva r) {
        Reembolso reembolso = reembolsoRepository.findByReservaId(r.getId()).orElse(null);
        String statusReembolso = reembolso == null ? StatusReembolso.NAO_APLICAVEL.name() : reembolso.getStatus().name();
        BigDecimal valor = reembolso == null ? BigDecimal.ZERO : reembolso.getValor();
        String prazo = reembolso == null ? null
                : reembolso.getStatus() == StatusReembolso.PROCESSADO ? textos.get("reembolso.prazo")
                : reembolso.getStatus() == StatusReembolso.FALHA ? textos.get("reembolso.falha")
                : textos.get("reembolso.pendente");
        return new Cancelamento(r.getCanceladaEm(), r.getCanceladaPor(), r.getCancelamentoRegra(),
                r.getCancelamentoMotivo(), statusReembolso, valor, prazo);
    }

    /** "03/10/2026 14:05" em Brasilia, para textos de e-mail, notificacao e auditoria. */
    public String emBrasilia(Instant instante) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(instante.atZone(zona));
    }
}
