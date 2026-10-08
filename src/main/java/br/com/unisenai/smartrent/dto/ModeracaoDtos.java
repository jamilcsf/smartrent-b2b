package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.dto.AdminDtos.DiaTotal;
import br.com.unisenai.smartrent.dto.TelemetriaDtos.ContagemRotulo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos da area de moderacao do painel de admin (denuncias, decisoes automaticas, contas, avisos). */
public final class ModeracaoDtos {

    private ModeracaoDtos() {
    }

    // ---- Pedidos ----

    /** {@code decisao}: PROCEDENTE ou IMPROCEDENTE. Suspender so vale para procedente. */
    public record DecisaoDenunciaRequest(String decisao, String nota, boolean suspenderDenunciado,
                                         boolean avisarDenunciante, boolean avisarDenunciado) {
    }

    /** {@code decisao}: REVISADO (confirma o alerta) ou DESCARTADO (falso positivo: encerra o efeito automatico). */
    public record RevisaoAlertaRequest(String decisao, String nota) {
    }

    public record MotivoRequest(String motivo) {
    }

    public record MensagemAdminRequest(String nivel, String assunto, String texto, boolean copiarPorEmail) {
    }

    // ---- Respostas ----

    public record UsuarioResumo(Long id, String nome, String email, String papel, boolean ativo) {
    }

    public record Decisao(String tipo, String nota, String admin, Instant quando) {
    }

    public record DenunciaItem(Long id, String motivo, String status, Instant criadaEm, UsuarioResumo denunciante,
                               UsuarioResumo denunciado, String descricao, long denunciasContra) {
    }

    public record MensagemEvidencia(UUID codigo, String autor, Instant criadaEm, String texto) {
    }

    public record AcaoItem(Long id, String tipo, String motivo, UsuarioResumo usuario, String admin, Long denunciaId,
                           Long alertaId, Instant criadoEm) {
    }

    public record AlertaItem(Long id, String tipo, String explicacao, int contagem, Instant janelaInicio,
                             Instant janelaFim, String status, Instant criadoEm, UsuarioResumo usuario, String efeito,
                             boolean efeitoAtivo, Decisao revisao) {
    }

    public record DenunciaDetalhe(DenunciaItem resumo, String imovel, List<MensagemEvidencia> evidencias,
                                  long denunciasProcedentesContra, List<AlertaItem> alertasDoDenunciado,
                                  List<AcaoItem> historicoDoDenunciado, Decisao decisao) {
    }

    public record AutorFiltrado(Long usuarioId, String nome, long total) {
    }

    /** O que o filtro automatico de mensagens fez no periodo (so contagens: nenhum texto de mensagem). */
    public record ResumoFiltro(int dias, long mensagens, long comAcaoDoFiltro, List<ContagemRotulo> porCategoria,
                               List<DiaTotal> porDia, List<AutorFiltrado> autoresMaisFiltrados) {
    }

    public record DecisoesAutomaticas(List<AlertaItem> alertas, long alertasAbertos, long limitesAtivos,
                                      ResumoFiltro filtro) {
    }

    public record ContaSuspensa(UsuarioResumo usuario, String motivo, String por, Instant desde) {
    }

    public record ComunicadoEnviado(Long id, UsuarioResumo destinatario, String nivel, String assunto, String texto,
                                    String admin, Instant criadoEm, Instant lidoEm) {
    }

    public record NivelRemetenteItem(String codigo, String rotulo) {
    }

    /** Aviso como o usuario o ve. */
    public record ComunicadoRecebido(Long id, String remetente, String assunto, String texto, Instant criadoEm,
                                     boolean lido) {
    }
}
