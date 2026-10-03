package br.com.unisenai.smartrent.dto;

import br.com.unisenai.smartrent.model.enums.TipoImovel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Campos editaveis do anuncio: e o corpo do cadastro, o corpo do rascunho de
 * edicao e, serializado, o conteudo do rascunho no banco.
 *
 * <p>As mesmas regras valem no cadastro e na confirmacao de uma edicao. O
 * preco ({@code valorDiaria}) so e lido no rascunho de um anuncio publicado;
 * no cadastro ele nasce nulo e e definido depois, pela lista de pre-publicacao.
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true) // rascunhos antigos com campos ja removidos continuam legiveis
public record AnuncioDados(
        @NotBlank(message = "Informe o título do anúncio.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String titulo,

        @Size(max = 5000, message = "A descrição deve ter no máximo 5000 caracteres.")
        String descricao,

        @NotNull(message = "Informe o tipo do imóvel.")
        TipoImovel tipoImovel,

        @NotNull(message = "Informe a área em m².")
        @Positive(message = "A área deve ser maior que zero.")
        @Max(value = 100000, message = "Área acima do limite aceito.")
        Integer metragemQuadrada,

        @NotNull(message = "Informe o número de quartos.")
        @Min(value = 0, message = "O número de quartos não pode ser negativo.")
        @Max(value = 100, message = "Número de quartos acima do limite aceito.")
        Integer numeroQuartos,

        @NotNull(message = "Informe o número de banheiros.")
        @Min(value = 0, message = "O número de banheiros não pode ser negativo.")
        @Max(value = 100, message = "Número de banheiros acima do limite aceito.")
        Integer numeroBanheiros,

        @Min(value = 0, message = "O número de vagas não pode ser negativo.")
        @Max(value = 100, message = "Número de vagas acima do limite aceito.")
        Integer vagasGaragem,

        @NotNull(message = "Informe o limite de hóspedes.")
        @Min(value = 1, message = "O limite de hóspedes deve ser de ao menos 1.")
        @Max(value = 50, message = "O limite de hóspedes não pode passar de 50.")
        Integer capacidadeHospedes,

        @Size(max = 40, message = "Informe no máximo 40 comodidades.")
        List<@NotBlank(message = "Comodidade vazia.")
             @Size(max = 60, message = "Cada comodidade deve ter no máximo 60 caracteres.") String> comodidades,

        @NotBlank(message = "Informe o CEP.")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000.")
        String cep,

        @NotBlank(message = "Informe o logradouro.")
        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
        String logradouro,

        @NotBlank(message = "Informe o número.")
        @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
        String numero,

        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
        String complemento,

        @NotBlank(message = "Informe o bairro.")
        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
        String bairro,

        @NotBlank(message = "Informe a cidade.")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
        String cidade,

        @NotBlank(message = "Informe a UF.")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "UF inválida. Use duas letras.")
        String estado,

        @Min(value = 1, message = "A quantidade mínima de diárias deve ser de ao menos 1.")
        @Max(value = 365, message = "A quantidade mínima de diárias não pode passar de 365.")
        Integer minimoDiarias,

        @DecimalMin(value = "0.00", message = "A taxa de limpeza não pode ser negativa.")
        @DecimalMax(value = "100000.00", message = "Taxa de limpeza acima do limite aceito.")
        @Digits(integer = 6, fraction = 2, message = "A taxa de limpeza deve ter no máximo 2 casas decimais.")
        BigDecimal taxaLimpeza,

        @DecimalMin(value = "1.00", message = "O valor da diária deve ser de ao menos R$ 1,00.")
        BigDecimal valorDiaria) {
}
