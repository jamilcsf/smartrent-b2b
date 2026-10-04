package br.com.unisenai.smartrent.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Porta do processamento de video. A implementacao com FFmpeg
 * ({@link VideoProcessingFfmpeg}) pode ser trocada por um servico gerenciado
 * (Mux, Cloudflare Stream, AWS MediaConvert...) sem mexer no restante: quem usa
 * so enxerga "inspecionar" e "processar".
 */
public interface VideoProcessingService {

    /** O que se descobre lendo o arquivo (nunca o que o navegador declarou). */
    record Metadados(double duracaoSegundos, int largura, int altura, String formato) {
    }

    /**
     * Arquivos gerados. {@code poster} e {@code hlsMestre} podem ser nulos
     * (processamento basico); todos os caminhos ficam dentro da pasta de saida.
     */
    record Resultado(Path mp4, Path poster, Path hlsMestre, Metadados metadados) {
    }

    /** Falha de leitura ou processamento, com mensagem apropriada ao gestor. */
    class VideoInvalidoException extends RuntimeException {
        public VideoInvalidoException(String mensagem) {
            super(mensagem);
        }

        public VideoInvalidoException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    /** Valida o arquivo (tem trilha de video, duracao e resolucao legiveis) e devolve seus metadados. */
    Metadados inspecionar(Path arquivo);

    /**
     * Transcodifica para H.264 + AAC (MP4 com faststart, ate 1080p), gera poster e
     * HLS (quando possivel) e remove metadados (inclusive GPS). Idempotente:
     * repetir sobrescreve os mesmos arquivos de saida.
     */
    Resultado processar(Path original, Path pastaSaida) throws IOException;

    /** Descricao curta de como esta processando, para o log de subida. */
    String descricao();

    /** Aceita WebM? So com FFmpeg: MP4/MOV tem leitor proprio. */
    boolean aceitaWebm();
}
