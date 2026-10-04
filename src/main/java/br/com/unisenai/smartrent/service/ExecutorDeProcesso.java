package br.com.unisenai.smartrent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executor real, com {@link ProcessBuilder}: sem shell, com tempo maximo (o
 * processo e morto se estourar) e com a saida truncada para nao encher a memoria.
 * Isolamento de CPU e memoria e responsabilidade do ambiente de execucao
 * (contêiner com limites, usuario sem privilegios): o FFmpeg tambem recebe
 * {@code -threads} limitado.
 */
public class ExecutorDeProcesso implements ExecutorDeComando {

    private static final int MAX_SAIDA = 64 * 1024;

    @Override
    public Saida executar(List<String> argumentos, Duration limite) throws IOException {
        Process p = new ProcessBuilder(argumentos).redirectErrorStream(true).start();
        try {
            p.getOutputStream().close(); // nada a enviar: sem stdin interativo
        } catch (IOException ignorado) {
            // processo ja encerrou
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Thread leitor = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (saida.size() < MAX_SAIDA) {
                        saida.write(buf, 0, Math.min(n, MAX_SAIDA - saida.size()));
                    }
                }
            } catch (IOException ignorado) {
                // fluxo fechado ao matar o processo
            }
        }, "executor-saida");
        leitor.setDaemon(true);
        leitor.start();
        try {
            if (!p.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new IOException("Tempo limite de " + limite.toSeconds() + " s excedido.");
            }
            leitor.join(1000);
            return new Saida(p.exitValue(), saida.toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Execução interrompida.", e);
        }
    }
}
