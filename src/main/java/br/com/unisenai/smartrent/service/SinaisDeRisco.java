package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ExclusaoDadosProperties;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.StatusReserva;
import br.com.unisenai.smartrent.repository.AuditoriaContaRepository;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.ReembolsoRepository;
import br.com.unisenai.smartrent.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Sinais que ajudam a equipe a analisar um pedido de exclusao de dados (golpe, conta invadida,
 * tentativa de sumir depois de aplicar um golpe) e os impedimentos objetivos da aprovacao.
 * Os sinais NAO bloqueiam o pedido; o texto guardado traz so sim/nao e contagens, nenhum dado
 * pessoal.
 */
@Component
public class SinaisDeRisco {

    private static final EnumSet<StatusReserva> VIGENTES = EnumSet.of(StatusReserva.PENDENTE, StatusReserva.CONFIRMADA);
    private static final EnumSet<StatusAnuncio> ATIVOS = EnumSet.of(StatusAnuncio.PUBLICADO, StatusAnuncio.EM_EDICAO,
            StatusAnuncio.REPUBLICACAO_AGENDADA, StatusAnuncio.PRONTO_PARA_PUBLICAR);

    private final AuditoriaContaRepository auditoria;
    private final ReservaRepository reservas;
    private final ImovelRepository imoveis;
    private final ReembolsoRepository reembolsos;
    private final DenunciaChatRepository denuncias;
    private final ExclusaoDadosProperties props;
    private final Clock clock;

    public SinaisDeRisco(AuditoriaContaRepository auditoria, ReservaRepository reservas, ImovelRepository imoveis,
                         ReembolsoRepository reembolsos, DenunciaChatRepository denuncias,
                         ExclusaoDadosProperties props, Clock clock) {
        this.auditoria = auditoria;
        this.reservas = reservas;
        this.imoveis = imoveis;
        this.reembolsos = reembolsos;
        this.denuncias = denuncias;
        this.props = props;
        this.clock = clock;
    }

    /** Texto dos sinais para gravar na solicitacao (chame ANTES de auditar o proprio pedido). */
    public String resumo(Long usuarioId, String ip) {
        Instant desde = clock.instant().minus(Duration.ofDays(props.riscoDias()));
        boolean emailRecente = !auditoria.recentes(usuarioId, desde, List.of(AuditoriaContaService.EMAIL_TROCA_CONFIRMADA)).isEmpty();
        boolean senhaRecente = !auditoria.recentes(usuarioId, desde, List.of(AuditoriaContaService.SENHA_ALTERADA)).isEmpty();
        String ipNovo = ip == null || ip.isBlank() ? "INDEFINIDO" : (auditoria.existeDoIp(usuarioId, ip, desde) ? "NAO" : "SIM");
        LocalDate hoje = LocalDate.now(clock);
        return "email_alterado_recentemente=" + sn(emailRecente)
                + "; senha_alterada_recentemente=" + sn(senhaRecente)
                + "; ip_novo=" + ipNovo
                + "; reservas_vigentes_como_cliente=" + reservas.contarVigentesDoCliente(usuarioId, VIGENTES, hoje)
                + "; reservas_vigentes_como_gestor=" + reservas.contarVigentesDoGestor(usuarioId, VIGENTES, hoje)
                + "; anuncios_ativos=" + imoveis.countByUsuarioIdAndStatusIn(usuarioId, ATIVOS)
                + "; reembolsos_nao_concluidos=" + reembolsos.contarNaoConcluidosDoUsuario(usuarioId)
                + "; denuncias_abertas_contra=" + denuncias.contarAbertasContra(usuarioId)
                + "; janela_dias=" + props.riscoDias();
    }

    /**
     * Impedimentos objetivos para aprovar a exclusao: reservas futuras ou em andamento (como
     * cliente ou como gestor), reembolso pendente, denuncia aberta contra o usuario e conversa
     * com disputa. Lista vazia = nada impede.
     */
    public List<String> impedimentos(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        List<String> lista = new ArrayList<>();
        long comoCliente = reservas.contarVigentesDoCliente(usuarioId, VIGENTES, hoje);
        long comoGestor = reservas.contarVigentesDoGestor(usuarioId, VIGENTES, hoje);
        long reembolsosPendentes = reembolsos.contarNaoConcluidosDoUsuario(usuarioId);
        long denunciasAbertas = denuncias.contarAbertasContra(usuarioId);
        long disputas = denuncias.contarConversasEmDisputa(usuarioId);
        if (comoCliente > 0) {
            lista.add("RESERVAS_VIGENTES_COMO_CLIENTE: " + comoCliente + " reserva(s) futura(s) ou em andamento");
        }
        if (comoGestor > 0) {
            lista.add("RESERVAS_VIGENTES_COMO_GESTOR: " + comoGestor + " reserva(s) futura(s) ou em andamento nos imóveis do usuário");
        }
        if (reembolsosPendentes > 0) {
            lista.add("REEMBOLSO_PENDENTE: " + reembolsosPendentes + " reembolso(s) não concluído(s)");
        }
        if (denunciasAbertas > 0) {
            lista.add("DENUNCIA_ABERTA: " + denunciasAbertas + " denúncia(s) aberta(s) contra o usuário");
        }
        if (disputas > 0) {
            lista.add("CONVERSA_COM_DISPUTA: " + disputas + " conversa(s) do SmartChat com denúncia aberta");
        }
        return lista;
    }

    private static String sn(boolean b) {
        return b ? "SIM" : "NAO";
    }
}
