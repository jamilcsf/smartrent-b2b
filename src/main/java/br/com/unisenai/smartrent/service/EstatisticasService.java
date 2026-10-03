package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.EstatisticasResponse;
import br.com.unisenai.smartrent.dto.EstatisticasResponse.Mes;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estatisticas do Dashboard. O filtro pelo gestor e feito aqui, no servidor:
 * so entram imoveis e reservas do usuario autenticado. Datas de calendario
 * (check-in/out) sao {@link LocalDate}, e "hoje" vem do relogio da plataforma
 * (horario de Brasilia), nunca do navegador.
 */
@Service
public class EstatisticasService {

    public static final int MESES_PADRAO = 6;
    public static final int MESES_MAX = 24;

    private final ImovelRepository imovelRepository;
    private final ReservaRepository reservaRepository;
    private final ImovelAcesso acesso;
    private final Clock clock;

    public EstatisticasService(ImovelRepository imovelRepository, ReservaRepository reservaRepository,
                               ImovelAcesso acesso, Clock clock) {
        this.imovelRepository = imovelRepository;
        this.reservaRepository = reservaRepository;
        this.acesso = acesso;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EstatisticasResponse calcular(Usuario gestor, Integer mesesPedidos) {
        acesso.exigirGestor(gestor);
        int meses = mesesPedidos == null ? MESES_PADRAO : Math.max(1, Math.min(MESES_MAX, mesesPedidos));
        YearMonth ultimo = YearMonth.from(LocalDate.now(clock));
        YearMonth primeiro = ultimo.minusMonths(meses - 1L);

        List<Imovel> imoveis = imovelRepository.findByUsuarioId(gestor.getId());
        List<Reserva> reservas = reservaRepository.findByImovelUsuarioIdOrderByDataCheckinDesc(gestor.getId());
        return agregar(imoveis, reservas, primeiro, ultimo);
    }

    /** Agregacao pura (sem banco), para testar com dados em memoria. */
    static EstatisticasResponse agregar(List<Imovel> imoveis, List<Reserva> reservas,
                                        YearMonth primeiro, YearMonth ultimo) {
        LocalDate de = primeiro.atDay(1);
        LocalDate ate = ultimo.atEndOfMonth();

        Map<StatusAnuncio, Long> porStatus = new EnumMap<>(StatusAnuncio.class);
        for (Imovel i : imoveis) {
            porStatus.merge(i.getStatus(), 1L, Long::sum);
        }
        Map<String, Long> imoveisPorStatus = new LinkedHashMap<>();
        for (StatusAnuncio s : StatusAnuncio.values()) {
            imoveisPorStatus.put(s.name(), porStatus.getOrDefault(s, 0L));
        }

        Map<YearMonth, long[]> contagem = new LinkedHashMap<>();      // reservas, canceladas, noites
        Map<YearMonth, BigDecimal[]> valores = new LinkedHashMap<>(); // bruta, reembolsos
        for (YearMonth m = primeiro; !m.isAfter(ultimo); m = m.plusMonths(1)) {
            contagem.put(m, new long[3]);
            valores.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        long totalReservas = 0;
        long canceladas = 0;
        long noitesOcupadas = 0;
        BigDecimal bruta = BigDecimal.ZERO;
        BigDecimal reemb = BigDecimal.ZERO;

        for (Reserva r : reservas) {
            YearMonth mesDaReserva = YearMonth.from(r.getDataCheckin());
            long[] c = contagem.get(mesDaReserva);
            if (r.getStatus().cancelada()) {
                if (c != null) {
                    c[1]++;
                    canceladas++;
                }
                continue; // cancelada nunca conta como ocupacao
            }
            if (c != null) {
                c[0]++;
                totalReservas++;
            }
            if (!r.getStatus().ocupaDatas()) {
                continue; // pendente ainda nao e ocupacao nem receita
            }
            for (LocalDate d = r.getDataCheckin(); d.isBefore(r.getDataCheckout()); d = d.plusDays(1)) {
                long[] cm = contagem.get(YearMonth.from(d));
                if (cm != null) {
                    cm[2]++;
                    noitesOcupadas++;
                }
            }
            if (c != null) {
                BigDecimal[] v = valores.get(mesDaReserva);
                v[0] = v[0].add(r.getTotalSnapshot());
                bruta = bruta.add(r.getTotalSnapshot());
            }
        }

        long imoveisNoAr = imoveis.stream().filter(i -> i.getStatus() == StatusAnuncio.PUBLICADO
                || i.getStatus() == StatusAnuncio.EM_EDICAO
                || i.getStatus() == StatusAnuncio.REPUBLICACAO_AGENDADA).count();
        long dias = ChronoUnit.DAYS.between(de, ate) + 1;
        long noitesBloqueadas = 0; // preenchido pelo bloqueio manual de datas
        long disponiveis = Math.max(0, dias * imoveisNoAr - noitesBloqueadas);
        double taxa = disponiveis == 0 ? 0 : Math.min(1.0, (double) noitesOcupadas / disponiveis);

        List<Mes> lista = new ArrayList<>();
        for (Map.Entry<YearMonth, long[]> e : contagem.entrySet()) {
            BigDecimal[] v = valores.get(e.getKey());
            lista.add(new Mes(e.getKey().toString(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                    v[0], v[1], v[0].subtract(v[1])));
        }
        BigDecimal ticket = totalReservas == 0 ? BigDecimal.ZERO
                : bruta.divide(BigDecimal.valueOf(totalReservas), 2, RoundingMode.HALF_UP);

        return new EstatisticasResponse(de, ate, imoveis.size(), imoveisPorStatus, totalReservas,
                canceladas, noitesOcupadas, noitesBloqueadas, disponiveis, taxa,
                bruta, reemb, bruta.subtract(reemb), ticket, lista);
    }
}
