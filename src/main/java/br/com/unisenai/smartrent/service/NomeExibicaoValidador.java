package br.com.unisenai.smartrent.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Regras do nome de exibicao (visivel a outros usuarios, como no SmartChat): 2 a 60
 * caracteres, letras (com acento), numeros, espacos e pontuacao basica; sem HTML. Rejeita
 * (nao mascara) nome com telefone, e-mail, link ou termos ofensivos, reaproveitando o
 * {@link MessageFilterService} do chat: uma unica fonte das regras de conteudo.
 */
@Component
public class NomeExibicaoValidador {

    public static final int MIN = 2;
    public static final int MAX = 60;

    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final Pattern PERMITIDO = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} .,'’\\-]*$");
    private static final Pattern TEM_LETRA = Pattern.compile("\\p{L}");

    private final MessageFilterService filtro;

    public NomeExibicaoValidador(MessageFilterService filtro) {
        this.filtro = filtro;
    }

    /** Devolve o nome normalizado ou lanca {@link IllegalArgumentException} com a mensagem para o usuario. */
    public String validar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("Informe o nome de exibição.");
        }
        String nome = ESPACOS.matcher(Normalizer.normalize(bruto, Normalizer.Form.NFC)).replaceAll(" ").trim();
        int tamanho = nome.codePointCount(0, nome.length());
        if (tamanho < MIN || tamanho > MAX) {
            throw new IllegalArgumentException("O nome de exibição deve ter entre " + MIN + " e " + MAX + " caracteres.");
        }
        if (!PERMITIDO.matcher(nome).matches() || !TEM_LETRA.matcher(nome).find()) {
            throw new IllegalArgumentException(
                    "Use apenas letras, números, espaços e pontuação básica (. , ' -) no nome de exibição.");
        }
        var resultado = filtro.filtrar(nome);
        if (resultado.alterado()) {
            throw new IllegalArgumentException(
                    "O nome de exibição não pode conter telefone, e-mail, link ou termos ofensivos.");
        }
        return nome;
    }
}
