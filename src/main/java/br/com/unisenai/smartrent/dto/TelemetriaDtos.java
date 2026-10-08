package br.com.unisenai.smartrent.dto;

import java.time.LocalDate;
import java.util.List;

/** Contratos da coleta (publica) e da analise (admin) da telemetria de uso. */
public final class TelemetriaDtos {

    private TelemetriaDtos() {
    }

    /** Lote enviado pelo navegador. Campos desconhecidos ou invalidos sao descartados pelo servico, nunca confiados. */
    public record LoteRequest(String sessaoId, String dispositivo, List<EventoRequest> eventos) {
    }

    /** Um evento do lote. O relogio do cliente nao entra: a hora vem do servidor. */
    public record EventoRequest(String tipo, String pagina, String alvo, Integer x, Integer y, Integer altura,
                                Integer valor) {
    }

    public record LoteResponse(int aceitos, int descartados) {
    }

    // ---- Analise (admin) ----

    public record PaginaVista(String pagina, long visualizacoes, long sessoes) {
    }

    public record ElementoClicado(String alvo, String pagina, long cliques) {
    }

    public record CelulaCalor(int coluna, int linha, long cliques) {
    }

    public record DiaUso(LocalDate dia, long visualizacoes, long sessoes) {
    }

    public record AtividadeHora(int diaSemana, int hora, long eventos) {
    }

    public record Permanencia(String pagina, double segundosMedios, long amostras) {
    }

    public record ContagemRotulo(String rotulo, long total) {
    }

    /** Mapa de calor de uma pagina: grade de cliques mais o necessario para desenha-la por cima da pagina. */
    public record MapaDeCalor(String pagina, String dispositivo, int dias, long totalCliques, long maximoCelula,
                              int colunas, int alturaCelulaPx, int alturaDocumentoPx, List<CelulaCalor> celulas,
                              List<ElementoClicado> elementos, List<ContagemRotulo> profundidadeRolagem,
                              long visualizacoes) {
    }

    public record ResumoUso(int dias, long visualizacoes, long cliques, long sessoes, double segundosMediosNaPagina,
                            double cliquesPorSessao, List<DiaUso> serieDiaria, List<PaginaVista> paginas,
                            List<ElementoClicado> elementos, List<AtividadeHora> atividade,
                            List<ContagemRotulo> dispositivos, List<ContagemRotulo> papeis,
                            List<Permanencia> permanencia, long eventosArmazenados) {
    }
}
