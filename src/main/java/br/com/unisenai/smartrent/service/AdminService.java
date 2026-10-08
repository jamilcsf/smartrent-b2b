package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.AdminDtos.ImovelAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.Pagina;
import br.com.unisenai.smartrent.dto.AdminDtos.ReservaAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.UsuarioAdmin;
import br.com.unisenai.smartrent.dto.AdminDtos.VisaoGeral;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Reserva;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Painel de admin: visao geral da plataforma e listagens de usuarios, imoveis e reservas (somente leitura). A
 * autorizacao (papel ADMIN) e do SecurityConfig. Suspender/reativar contas e demais acoes ficam no
 * {@link ModeracaoService}. Listagens limitam o tamanho da pagina e escapam o texto da busca.
 */
@Service
public class AdminService {

    static final int TAMANHO_MAX = 100;
    static final int DIAS_PADRAO = 30;
    static final int DIAS_MAX = 365;

    private final UsuarioRepository usuarios;
    private final ImovelRepository imoveis;
    private final ReservaRepository reservas;
    private final DataDeletionReviewService exclusoes;
    private final DenunciaChatRepository denuncias;
    private final TelemetriaAnaliseService telemetria;
    private final Clock clock;

    public AdminService(UsuarioRepository usuarios, ImovelRepository imoveis, ReservaRepository reservas,
                        DataDeletionReviewService exclusoes, DenunciaChatRepository denuncias,
                        TelemetriaAnaliseService telemetria, Clock clock) {
        this.usuarios = usuarios;
        this.imoveis = imoveis;
        this.reservas = reservas;
        this.exclusoes = exclusoes;
        this.denuncias = denuncias;
        this.telemetria = telemetria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VisaoGeral visaoGeral(Integer diasPedidos) {
        int dias = diasPedidos == null ? DIAS_PADRAO : Math.max(1, Math.min(diasPedidos, DIAS_MAX));
        LocalDateTime desde = LocalDateTime.now(clock).toLocalDate().minusDays(dias - 1L).atStartOfDay();
        var porStatusReserva = reservas.contagemPorStatus();
        var porStatusImovel = imoveis.contagemPorStatus();
        long reservasNoPeriodo = reservas.countByDataCriacaoGreaterThanEqual(desde);
        long sessoesNoDetalhe = telemetria.sessoesNoDetalhe(dias);
        return new VisaoGeral(dias,
                usuarios.count(), usuarios.countByAtivoFalse(), usuarios.countByDataCriacaoGreaterThanEqual(desde),
                usuarios.contagemPorPapel(),
                porStatusImovel.stream().mapToLong(c -> c.total()).sum(), porStatusImovel,
                porStatusReserva.stream().mapToLong(c -> c.total()).sum(), reservasNoPeriodo, porStatusReserva,
                reservas.valorMovimentadoDesde(desde),
                exclusoes.contarEmAndamento(), denuncias.countByStatus("PENDENTE"),
                sessoesNoDetalhe, sessoesNoDetalhe == 0 ? 0 : (double) reservasNoPeriodo / sessoesNoDetalhe,
                usuarios.cadastrosPorDia(desde), reservas.reservasPorDia(desde));
    }

    // ---- Listagens ----

    @Transactional(readOnly = true)
    public Pagina<UsuarioAdmin> usuarios(String busca, String papel, Integer pagina, Integer tamanho) {
        PapelUsuario filtro = enumOuNulo(PapelUsuario.class, papel);
        Specification<Usuario> spec = (raiz, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filtro != null) {
                ps.add(cb.equal(raiz.get("papel"), filtro));
            }
            String like = padrao(busca);
            if (like != null) {
                ps.add(cb.or(cb.like(cb.lower(raiz.get("nome")), like, '\\'), cb.like(cb.lower(raiz.get("email")), like, '\\')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return paginar(usuarios.findAll(spec, pageable(pagina, tamanho, "id")), u -> new UsuarioAdmin(u.getId(), u.getNome(),
                u.getEmail(), u.getPapel().name(), u.isAtivo(), u.isEmailVerificado(), u.getDataCriacao()));
    }

    @Transactional(readOnly = true)
    public Pagina<ImovelAdmin> imoveis(String busca, String status, Integer pagina, Integer tamanho) {
        StatusAnuncio filtro = enumOuNulo(StatusAnuncio.class, status);
        Specification<Imovel> spec = (raiz, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filtro != null) {
                ps.add(cb.equal(raiz.get("status"), filtro));
            }
            String like = padrao(busca);
            if (like != null) {
                ps.add(cb.like(cb.lower(raiz.get("titulo")), like, '\\'));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return paginar(imoveis.findAll(spec, pageable(pagina, tamanho, "id")), i -> new ImovelAdmin(i.getId(), i.getTitulo(),
                local(i), i.getUsuario() == null ? null : i.getUsuario().getNome(), i.getStatus().name(), i.isAtivo(),
                i.getValorDiariaBase(), i.getPublicadoEm(), i.getDataCadastro()));
    }

    @Transactional(readOnly = true)
    public Pagina<ReservaAdmin> reservas(String busca, String status, Integer pagina, Integer tamanho) {
        StatusReserva filtro = enumOuNulo(StatusReserva.class, status);
        Specification<Reserva> spec = (raiz, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filtro != null) {
                ps.add(cb.equal(raiz.get("status"), filtro));
            }
            String like = padrao(busca);
            if (like != null) {
                ps.add(cb.or(cb.like(cb.lower(raiz.get("imovelTituloSnapshot")), like, '\\'),
                        cb.like(cb.lower(raiz.get("hospedeNome")), like, '\\')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return paginar(reservas.findAll(spec, pageable(pagina, tamanho, "id")), r -> new ReservaAdmin(r.getId(),
                r.getImovelTituloSnapshot(), r.getHospedeNome(), r.getDataCheckin(), r.getDataCheckout(),
                r.getStatus().name(), r.getOrigem().name(), r.getTotalSnapshot(), r.getDataCriacao()));
    }

    // ---- Apoio ----

    private static String local(Imovel i) {
        if (i.getEndereco() == null) {
            return null;
        }
        String bairro = i.getEndereco().getBairro();
        String cidade = i.getEndereco().getCidade();
        return bairro == null ? cidade : cidade == null ? bairro : bairro + ", " + cidade;
    }

    private static PageRequest pageable(Integer pagina, Integer tamanho, String ordem) {
        int p = pagina == null ? 0 : Math.max(0, pagina);
        int t = tamanho == null ? 20 : Math.max(1, Math.min(tamanho, TAMANHO_MAX));
        return PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, ordem));
    }

    private static <E, T> Pagina<T> paginar(Page<E> pagina, Function<E, T> mapa) {
        return new Pagina<>(pagina.getContent().stream().map(mapa).toList(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** Padrao LIKE "contem", com % e _ do texto digitado escapados (nao viram curinga). Nulo quando nao ha busca. */
    static String padrao(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String t = busca.trim().toLowerCase(Locale.ROOT);
        t = t.substring(0, Math.min(t.length(), 80)).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + t + "%";
    }

    private static <E extends Enum<E>> E enumOuNulo(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
