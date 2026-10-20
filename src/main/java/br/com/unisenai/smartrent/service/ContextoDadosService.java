package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Dados da propria plataforma que alimentam a IA de preco: historico do imovel
 * e panorama agregado de anuncios semelhantes (mesmo tipo e cidade).
 *
 * <p><b>Privacidade:</b> so saem numeros agregados. O panorama exige pelo menos
 * {@link #MINIMO_AMOSTRA} outros anuncios; abaixo disso nao e informado, para
 * nao permitir identificar o preco ou a ocupacao de um concorrente especifico.
 * Nenhum nome, e-mail, endereco exato ou texto livre e incluido.
 */
@Service
public class ContextoDadosService {

    static final int JANELA_DIAS = 90;
    static final int MINIMO_AMOSTRA = 3;

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final Clock clock;

    public ContextoDadosService(ReservaRepository reservaRepository, ImovelRepository imovelRepository, Clock clock) {
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.clock = clock;
    }

    /** Ocupacao e diaria media praticada em uma janela; {@code diariaMedia} e nulo sem noites vendidas. */
    public record Desempenho(long noitesOcupadas, long noitesDisponiveis, BigDecimal diariaMedia) {
        public double ocupacao() {
            return noitesDisponiveis == 0 ? 0 : Math.min(1.0, (double) noitesOcupadas / noitesDisponiveis);
        }
    }

    /** Panorama dos anuncios semelhantes: preco anunciado (mediana/faixa) e desempenho agregado. */
    public record Mercado(int anuncios, BigDecimal medianaAnunciada, BigDecimal minAnunciada,
                          BigDecimal maxAnunciada, Desempenho desempenho) {
    }

    /** {@code proprio} e {@code mercado} podem ser nulos (sem dado suficiente). */
    public record Contexto(Desempenho proprio, Mercado mercado) {
    }

    public Contexto montar(Imovel imovel) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate de = hoje.minusDays(JANELA_DIAS);
        Desempenho proprio = null;
        if (imovel.getId() != null) {
            proprio = desempenho(reservaRepository.findOcupacaoNoPeriodo(List.of(imovel.getId()), de, hoje), 1, de, hoje);
            if (proprio.noitesOcupadas() == 0) {
                proprio = null; // imovel novo ou sem vendas: nao ha historico a mostrar
            }
        }

        Mercado mercado = null;
        if (imovel.getId() != null && imovel.getEndereco() != null && imovel.getTipoImovel() != null) {
            List<Imovel> semelhantes = imovelRepository.findSemelhantesNoAr(imovel.getId(), imovel.getTipoImovel(),
                    imovel.getEndereco().getCidade(), Agora.de(clock));
            if (semelhantes.size() >= MINIMO_AMOSTRA) {
                List<Long> ids = semelhantes.stream().map(Imovel::getId).toList();
                mercado = mercado(semelhantes, reservaRepository.findOcupacaoNoPeriodo(ids, de, hoje), de, hoje);
            }
        }
        return new Contexto(proprio, mercado);
    }

    /** Agregacao pura (sem banco), para testar com dados em memoria. */
    static Mercado mercado(List<Imovel> anuncios, Collection<Reserva> reservas, LocalDate de, LocalDate ate) {
        List<BigDecimal> precos = anuncios.stream().map(Imovel::getValorDiariaBase)
                .filter(v -> v != null && v.signum() > 0).sorted(Comparator.naturalOrder()).toList();
        BigDecimal mediana = null;
        if (!precos.isEmpty()) {
            int n = precos.size();
            mediana = n % 2 == 1 ? precos.get(n / 2)
                    : precos.get(n / 2 - 1).add(precos.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        return new Mercado(anuncios.size(), mediana,
                precos.isEmpty() ? null : precos.get(0), precos.isEmpty() ? null : precos.get(precos.size() - 1),
                desempenho(reservas, anuncios.size(), de, ate));
    }

    /** Noites de reserva dentro de [de, ate) e diaria media ponderada pelas noites. */
    static Desempenho desempenho(Collection<Reserva> reservas, int imoveis, LocalDate de, LocalDate ate) {
        long noites = 0;
        BigDecimal soma = BigDecimal.ZERO;
        for (Reserva r : reservas) {
            LocalDate ini = r.getDataCheckin().isBefore(de) ? de : r.getDataCheckin();
            LocalDate fim = r.getDataCheckout().isAfter(ate) ? ate : r.getDataCheckout();
            long n = ChronoUnit.DAYS.between(ini, fim);
            if (n > 0 && r.getPrecoDiariaSnapshot() != null) {
                noites += n;
                soma = soma.add(r.getPrecoDiariaSnapshot().multiply(BigDecimal.valueOf(n)));
            }
        }
        BigDecimal media = noites == 0 ? null : soma.divide(BigDecimal.valueOf(noites), 2, RoundingMode.HALF_UP);
        return new Desempenho(noites, ChronoUnit.DAYS.between(de, ate) * (long) imoveis, media);
    }

    /** Texto para o prompt; vazio quando nao ha dado a informar. */
    static String paraPrompt(Contexto c) {
        if (c == null || (c.proprio() == null && c.mercado() == null)) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Dados reais da plataforma (últimos ")
                .append(JANELA_DIAS).append(" dias, agregados e anônimos):\n");
        if (c.proprio() != null) {
            sb.append("- Este imóvel: ocupação ").append(pct(c.proprio().ocupacao()))
              .append(", diária média praticada ").append(brl(c.proprio().diariaMedia())).append('\n');
        } else {
            sb.append("- Este imóvel: sem histórico de reservas.\n");
        }
        Mercado m = c.mercado();
        if (m != null) {
            sb.append("- ").append(m.anuncios()).append(" anúncios semelhantes (mesmo tipo e cidade): diária anunciada mediana ")
              .append(brl(m.medianaAnunciada())).append(" (faixa ").append(brl(m.minAnunciada()))
              .append(" a ").append(brl(m.maxAnunciada())).append("), ocupação média ")
              .append(pct(m.desempenho().ocupacao())).append(", diária média praticada ")
              .append(brl(m.desempenho().diariaMedia())).append('\n');
        }
        return sb.append("Use esses dados para calibrar o valor e cite na justificativa quais usou.\n").toString();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String brl(BigDecimal v) {
        return v == null ? "n/d" : "R$ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
