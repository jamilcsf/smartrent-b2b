package br.com.unisenai.smartrent.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Executa um programa externo com argumentos JA separados (nunca uma linha de
 * shell) e com limite de tempo. Existe como interface para o FFmpeg poder ser
 * testado sem o binario e para que NENHUM argumento seja montado a partir de
 * nome de arquivo ou metadado enviado pelo usuario.
 */
public interface ExecutorDeComando {

    record Saida(int codigo, String texto) {
    }

    /** Falha por tempo esgotado ou por nao conseguir iniciar o processo. */
    Saida executar(List<String> argumentos, Duration limite) throws IOException;
}
