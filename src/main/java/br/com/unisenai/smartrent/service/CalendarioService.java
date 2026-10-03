package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.CalendarioDtos.BloqueioResposta;
import br.com.unisenai.smartrent.dto.CalendarioDtos.Calendario;
import br.com.unisenai.smartrent.dto.CalendarioDtos.Faixa;
import br.com.unisenai.smartrent.dto.CalendarioDtos.ImovelResumo;
import br.com.unisenai.smartrent.dto.CalendarioDtos.ReservaItem;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.BloqueioDataRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calendario do gestor (reservas e bloqueios de UM imovel por vez) e faixas de
 * indisponibilidade para clientes. Os dados do hospede so saem para o dono do
 * imovel (conferido por {@link ImovelAcesso}) e vem do snapshot da reserva; para
 * clientes, reserva e bloqueio viram a mesma "faixa indisponivel".
 */
@Service
public class CalendarioService {

    /** Janela maxima consultada de uma vez (3 meses visiveis + folga de navegacao). */
    static final long MAX_DIAS = 400;

    private final ImovelAcesso acesso;
    private final ImovelRepository imovelRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueioDataRepository bloqueioRepository;
    private final Clock clock;
    private final ZoneId zona;

    public CalendarioService(ImovelAcesso acesso, ImovelRepository imovelRepository,
                             ReservaRepository reservaRepository, BloqueioDataRepository bloqueioRepository,
                             Clock clock, ZoneId zonaDaPlataforma) {
        this.acesso = acesso;
        this.imovelRepository = imovelRepository;
        this.reservaRepository = reservaRepository;
        this.bloqueioRepository = bloqueioRepository;
        this.clock = clock;
        this.zona = zonaDaPlataforma;
    }

    /** Intervalo [de, ate] inclusive; reservas e bloqueios do imovel selecionado, nunca de outros. */
    @Transactional(readOnly = true)
    public Calendario calendario(Usuario gestor, Long imovelId, LocalDate de, LocalDate ate) {
        Imovel imovel = acesso.doGestor(gestor, imovelId);
        validarJanela(de, ate);

        List<ReservaItem> reservas = reservaRepository.findAtivasNoPeriodo(imovel.getId(), de, ate.plusDays(1)).stream()
                .map(r -> new ReservaItem(r.getId(), r.getStatus(), r.getDataCheckin(), r.getDataCheckout(),
                        r.getHospedeNome(), r.getHospedeEmail(), r.getNumeroHospedes(), r.getCliente() != null))
                .toList();
        List<BloqueioResposta> bloqueios = bloqueioRepository.findNoPeriodo(imovel.getId(), de, ate).stream()
                .map(BloqueioResposta::de).toList();
        return new Calendario(new ImovelResumo(imovel.getId(), CodigoImovel.de(imovel.getId()), imovel.getTitulo()),
                de, ate, LocalDate.now(clock), zona.getId(), reservas, bloqueios);
    }

    /**
     * Noites indisponiveis de um anuncio publicado, para o cliente: junta reservas
     * ativas e bloqueios sem dizer qual e qual nem por que.
     */
    @Transactional(readOnly = true)
    public List<Faixa> indisponibilidade(Long imovelId, LocalDate de, LocalDate ate) {
        imovelRepository.findVisivelPorId(imovelId, Agora.de(clock))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado."));
        validarJanela(de, ate);

        List<Faixa> bruto = new ArrayList<>();
        reservaRepository.findAtivasNoPeriodo(imovelId, de, ate.plusDays(1))
                .forEach(r -> bruto.add(new Faixa(r.getDataCheckin(), r.getDataCheckout().minusDays(1))));
        bloqueioRepository.findNoPeriodo(imovelId, de, ate)
                .forEach(b -> bruto.add(new Faixa(b.getDataInicio(), b.getDataFim())));

        bruto.sort(Comparator.comparing(Faixa::inicio));
        List<Faixa> unidas = new ArrayList<>();
        for (Faixa f : bruto) {
            if (!unidas.isEmpty() && !f.inicio().isAfter(unidas.get(unidas.size() - 1).fim().plusDays(1))) {
                Faixa u = unidas.get(unidas.size() - 1);
                unidas.set(unidas.size() - 1, new Faixa(u.inicio(), f.fim().isAfter(u.fim()) ? f.fim() : u.fim()));
            } else {
                unidas.add(f);
            }
        }
        return unidas;
    }

    private static void validarJanela(LocalDate de, LocalDate ate) {
        if (de == null || ate == null || ate.isBefore(de)) {
            throw new IllegalArgumentException("Informe um período válido (de/até).");
        }
        if (ChronoUnit.DAYS.between(de, ate) > MAX_DIAS) {
            throw new IllegalArgumentException("O período consultado é grande demais (máximo " + MAX_DIAS + " dias).");
        }
    }
}
