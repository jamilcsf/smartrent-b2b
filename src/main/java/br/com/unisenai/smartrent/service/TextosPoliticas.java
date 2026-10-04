package br.com.unisenai.smartrent.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Textos da politica de cancelamento e reembolso, lidos de um unico recurso
 * ({@code textos-pendentes-juridico.properties}). Sao provisorios e pendentes de
 * revisao do setor juridico: trocar o texto nao exige mexer em codigo.
 */
@Component
public class TextosPoliticas {

    private final Properties textos = new Properties();

    public TextosPoliticas() {
        try (InputStream in = getClass().getResourceAsStream("/textos-pendentes-juridico.properties")) {
            if (in != null) {
                textos.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel carregar os textos da politica.", e);
        }
    }

    /** Texto com os marcadores %s preenchidos; chave ausente devolve a propria chave (nunca quebra a tela). */
    public String get(String chave, Object... args) {
        String modelo = textos.getProperty(chave);
        if (modelo == null) {
            return chave;
        }
        modelo = modelo.replace("\\n", "\n");
        return args.length == 0 ? modelo : String.format(modelo, args);
    }
}
