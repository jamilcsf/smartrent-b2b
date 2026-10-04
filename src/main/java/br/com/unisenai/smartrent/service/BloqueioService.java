package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioPedido;
import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioResposta;
import br.com.unisenai.smartrent.model.BloqueioData;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.BloqueioDataRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bloqueio manual de datas (manutencao, uso proprio, reserva externa...).
 *
 * <p><b>Concorrencia.</b> Toda operacao trava a linha do imovel
 * ({@code PESSIMISTIC_WRITE}), a mesma que a criacao de reservas trava; a checagem
 * de conflito e a gravacao acontecem na mesma transacao. Assim, uma reserva e um
 * bloqueio simultaneos nas mesmas datas nunca sao aceitos juntos.
 *
 * <p>As datas sao de calendario, inclusivas nas duas pontas. O bloqueio nunca
 * altera reservas existentes nem seus snapshots; reservas pendentes no periodo so
 * saem (canceladas sem cobranca) se o gestor confirmar. Motivo e observacao sao
 * internos e jamais chegam a clientes.
 */
@Service
public class BloqueioService {

    public static final Set<String> MOTIVOS = Set.of("MANUTENCAO", "USO_PROPRIO", "RESERVA_EXTERNA", "OUTRO");

    private final BloqueioDataRepository repository;
    private final ReservaRepository reservaRepository;
    private final ImovelAcesso acesso;
    private final AuditoriaService auditoria;
    private final CancelamentoService cancelamento;
    private final Clock clock;

    public BloqueioService(BloqueioDataRepository repository, ReservaRepository reservaRepository,
                           ImovelAcesso acesso, AuditoriaService auditoria, CancelamentoService cancelamento,
                           Clock clock) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.cancelamento = cancelamento;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BloqueioResposta> listar(Usuario gestor, Long imovelId) {
        Imovel imovel = acesso.doGestor(gestor, imovelId);
        return repository.findByImovelIdOrderByDataInicio(imovel.getId()).stream().map(BloqueioResposta::de).toList();
    }

    @Transactional
    public List<BloqueioResposta> criar(Usuario gestor, Long imovelId, BloqueioPedido p) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        validar(p);
        LocalDate inicio = p.dataInicio();
        LocalDate fim = p.dataFim();

        List<Reserva> tocadas = reservaRepository.findAtivasQueTocam(imovel.getId(), inicio, fim.plusDays(1));
        List<BloqueioData> existentes = repository.findNoPeriodo(imovel.getId(), inicio, fim);

        // Classifica cada noite: confirmada (conflito), ja bloqueada (ignora) ou livre.
        List<LocalDate> livres = new ArrayList<>();
        List<LocalDate> emConflito = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            final LocalDate noite = d;
            boolean confirmada = tocadas.stream().anyMatch(r -> r.getStatus().ocupaDatas() && ocupa(r, noite));
            boolean jaBloqueada = existentes.stream().anyMatch(b -> !noite.isBefore(b.getDataInicio()) && !noite.isAfter(b.getDataFim()));
            if (confirmada) {
                emConflito.add(d);
            } else if (!jaBloqueada) {
                livres.add(d);
            }
        }

        if (!emConflito.isEmpty() && !Boolean.TRUE.equals(p.apenasLivres())) {
            List<Map<String, Object>> detalhes = tocadas.stream().filter(r -> r.getStatus().ocupaDatas())
                    .map(r -> detalheReserva(r, "Reserva confirmada")).toList();
            throw new ConflitoComDetalhesException("RESERVA_CONFIRMADA_NO_PERIODO",
                    "Não é possível bloquear datas com reserva confirmada: " + intervalos(emConflito)
                            + ". Você pode bloquear apenas as datas livres.", detalhes);
        }
        if (livres.isEmpty()) {
            throw new IllegalArgumentException("Não há datas livres para bloquear neste período.");
        }
        List<LocalDate[]> segmentos = segmentos(livres);

        List<Reserva> pendentes = tocadas.stream().filter(r -> r.getStatus() == StatusReserva.PENDENTE)
                .filter(r -> livres.stream().anyMatch(d -> ocupa(r, d))).toList();
        if (!pendentes.isEmpty()) {
            if (!Boolean.TRUE.equals(p.cancelarPendentes())) {
                throw new ConflitoComDetalhesException("RESERVAS_PENDENTES_NO_PERIODO",
                        "Há reserva(s) pendente(s) neste período. Bloquear cancela essas reservas, sem cobrança.",
                        pendentes.stream().map(r -> detalheReserva(r, "Reserva pendente")).toList());
            }
            pendentes.forEach(r -> cancelamento.cancelarPendentePeloSistema(r, "Datas bloqueadas pelo gestor."));
        }

        String observacao = p.observacao() == null || p.observacao().isBlank() ? null : Sanitizador.texto(p.observacao());
        if (observacao != null && observacao.length() > 500) {
            throw new IllegalArgumentException("A observação deve ter no máximo 500 caracteres.");
        }
        List<BloqueioResposta> criados = new ArrayList<>();
        for (LocalDate[] s : segmentos) {
            criados.add(BloqueioResposta.de(gravar(imovel, gestor, s[0], s[1], p.motivo(), observacao)));
        }
        auditoria.acao(imovel, gestor.getId(), "BLOQUEIO_CRIADO",
                "imovel=" + CodigoImovel.de(imovel.getId()) + " periodo=" + inicio + " a " + fim
                        + " motivo=" + p.motivo() + " segmentos=" + segmentos.size());
        return criados;
    }

    /**
     * Remove o bloqueio inteiro ou apenas {@code de..ate} dele (inclusive). Remover o
     * miolo divide o bloqueio em dois, preservando motivo e observacao nas duas partes.
     */
    @Transactional
    public List<BloqueioResposta> remover(Usuario gestor, Long imovelId, Long bloqueioId, LocalDate de, LocalDate ate) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        BloqueioData b = repository.findById(bloqueioId)
                .filter(x -> x.getImovel().getId().equals(imovel.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado."));

        LocalDate remInicio = de == null ? b.getDataInicio() : de;
        LocalDate remFim = ate == null ? b.getDataFim() : ate;
        if (remFim.isBefore(remInicio) || remInicio.isBefore(b.getDataInicio()) || remFim.isAfter(b.getDataFim())) {
            throw new IllegalArgumentException("O período a remover deve estar dentro do bloqueio ("
                    + b.getDataInicio() + " a " + b.getDataFim() + ").");
        }

        List<BloqueioResposta> restantes = new ArrayList<>();
        LocalDate ini = b.getDataInicio();
        LocalDate fim = b.getDataFim();
        String motivo = b.getMotivo();
        String obs = b.getObservacao();
        repository.delete(b);
        repository.flush();
        if (ini.isBefore(remInicio)) {
            restantes.add(BloqueioResposta.de(gravar(imovel, gestor, ini, remInicio.minusDays(1), motivo, obs)));
        }
        if (fim.isAfter(remFim)) {
            restantes.add(BloqueioResposta.de(gravar(imovel, gestor, remFim.plusDays(1), fim, motivo, obs)));
        }
        auditoria.acao(imovel, gestor.getId(), "BLOQUEIO_REMOVIDO",
                "imovel=" + CodigoImovel.de(imovel.getId()) + " bloqueio=" + ini + " a " + fim
                        + " removido=" + remInicio + " a " + remFim);
        return restantes;
    }

    // ------------------------------------------------------------------ apoio

    private BloqueioData gravar(Imovel imovel, Usuario gestor, LocalDate inicio, LocalDate fim, String motivo, String obs) {
        BloqueioData b = new BloqueioData();
        b.setImovel(imovel);
        b.setDataInicio(inicio);
        b.setDataFim(fim);
        b.setMotivo(motivo);
        b.setObservacao(obs);
        b.setCriadoPor(gestor.getId());
        b.setCriadoEm(clock.instant());
        return repository.save(b);
    }

    private void validar(BloqueioPedido p) {
        if (p == null || p.dataInicio() == null || p.dataFim() == null) {
            throw new IllegalArgumentException("Informe a data inicial e a data final do bloqueio.");
        }
        if (p.dataFim().isBefore(p.dataInicio())) {
            throw new IllegalArgumentException("A data final do bloqueio não pode ser anterior à inicial.");
        }
        if (p.dataInicio().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Não é possível bloquear datas que já passaram.");
        }
        if (p.dataFim().isAfter(LocalDate.now(clock).plusYears(3))) {
            throw new IllegalArgumentException("O bloqueio não pode passar de 3 anos à frente.");
        }
        if (p.motivo() == null || !MOTIVOS.contains(p.motivo())) {
            throw new IllegalArgumentException("Informe o motivo do bloqueio (manutenção, uso próprio, reserva externa ou outro).");
        }
    }

    /** A reserva ocupa as noites de check-in ate a vespera do check-out. */
    static boolean ocupa(Reserva r, LocalDate noite) {
        return !noite.isBefore(r.getDataCheckin()) && noite.isBefore(r.getDataCheckout());
    }

    private static List<LocalDate[]> segmentos(List<LocalDate> datas) {
        List<LocalDate[]> out = new ArrayList<>();
        LocalDate ini = datas.get(0);
        LocalDate ant = ini;
        for (int i = 1; i < datas.size(); i++) {
            LocalDate d = datas.get(i);
            if (!d.equals(ant.plusDays(1))) {
                out.add(new LocalDate[]{ini, ant});
                ini = d;
            }
            ant = d;
        }
        out.add(new LocalDate[]{ini, ant});
        return out;
    }

    private static String intervalos(List<LocalDate> datas) {
        List<String> partes = new ArrayList<>();
        for (LocalDate[] s : segmentos(datas)) {
            partes.add(s[0].equals(s[1]) ? s[0].toString() : s[0] + " a " + s[1]);
        }
        return String.join(", ", partes);
    }

    private static Map<String, Object> detalheReserva(Reserva r, String rotulo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservaId", r.getId());
        m.put("tipo", rotulo);
        m.put("dataCheckin", r.getDataCheckin().toString());
        m.put("dataCheckout", r.getDataCheckout().toString());
        return m;
    }
}
