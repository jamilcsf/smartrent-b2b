package com.smartrentb2b.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Objeto de valor embutido em {@link Imovel}. Latitude/longitude são
 * obrigatórias por serem insumo direto do fator de microgeografia do motor
 * preditivo de precificação (Groq API / Llama 3.3).
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Endereco {

    @NotBlank(message = "O logradouro é obrigatório.")
    @Size(max = 150)
    @Column(name = "logradouro", nullable = false, length = 150)
    private String logradouro;

    @Size(max = 20)
    @Column(name = "numero", length = 20)
    private String numero;

    @Size(max = 100)
    @Column(name = "complemento", length = 100)
    private String complemento;

    @NotBlank(message = "O bairro é obrigatório.")
    @Size(max = 100)
    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;

    @NotBlank(message = "A cidade é obrigatória.")
    @Size(max = 100)
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @NotBlank(message = "A UF é obrigatória.")
    @Pattern(regexp = "^[A-Z]{2}$", message = "UF deve conter 2 letras maiúsculas (ex.: SC).")
    @Column(name = "estado", nullable = false, length = 2)
    private String estado;

    @NotBlank(message = "O CEP é obrigatório.")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000.")
    @Column(name = "cep", nullable = false, length = 9)
    private String cep;

    @NotNull(message = "A latitude é obrigatória para o cálculo de microgeografia.")
    @DecimalMin(value = "-90.0", message = "Latitude inválida.")
    @DecimalMax(value = "90.0", message = "Latitude inválida.")
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @NotNull(message = "A longitude é obrigatória para o cálculo de microgeografia.")
    @DecimalMin(value = "-180.0", message = "Longitude inválida.")
    @DecimalMax(value = "180.0", message = "Longitude inválida.")
    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
}