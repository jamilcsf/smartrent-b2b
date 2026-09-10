package com.smartrentb2b.domain.model;

import com.smartrentb2b.domain.model.enums.OrigemCalculoPreco;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro histórico de uma sugestão de preço gerada pelo motor preditivo
 * para uma data de referência de um {@link Imovel}. O campo
 * {@link #origemCalculo} garante a auditabilidade do requisito Must Have
 * "Sugestão IA com fallback": toda sugestão persistida deixa explícito se
 * veio da IA generativa (Groq API / Llama 3.3) ou da regra de negócio
 * determinística usada quando a IA está indisponível.
 */
@Entity
@Table(name = "sugestoes_preco", indexes = {
        @Index(name = "idx_sugestoes_imovel_data", columnList = "imovel_id, data_referencia")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "imovel")
public class SugestaoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "A sugestão deve estar vinculada a um imóvel.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sugestao_imovel"))
    private Imovel imovel;

    @NotNull(message = "A data de referência da sugestão é obrigatória.")
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @NotNull(message = "O valor base é obrigatório.")
    @DecimalMin(value = "0.01", message = "O valor base deve ser maior que zero.")
    @Digits(integer = 8, fraction = 2)
    @Column(name = "valor_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorBase;

    @NotNull(message = "O valor sugerido é obrigatório.")
    @DecimalMin(value = "0.01", message = "O valor sugerido deve ser maior que zero.")
    @Digits(integer = 8, fraction = 2)
    @Column(name = "valor_sugerido", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSugerido;

    @Digits(integer = 3, fraction = 2, message = "Percentual de ajuste inválido.")
    @Column(name = "percentual_ajuste", precision = 5, scale = 2)
    private BigDecimal percentualAjuste;

    @Digits(integer = 3, fraction = 2, message = "Fator de sazonalidade inválido.")
    @Column(name = "fator_sazonalidade", precision = 5, scale = 2)
    private BigDecimal fatorSazonalidade;

    @Digits(integer = 3, fraction = 2, message = "Fator de microgeografia inválido.")
    @Column(name = "fator_microgeografia", precision = 5, scale = 2)
    private BigDecimal fatorMicrogeografia;

    @Size(max = 2000, message = "Justificativa deve ter no máximo 2000 caracteres.")
    @Column(name = "justificativa_ia", columnDefinition = "TEXT")
    private String justificativaIa;

    @Size(max = 80)
    @Builder.Default
    @Column(name = "modelo_ia_utilizado", length = 80)
    private String modeloIaUtilizado = "llama-3.3-70b-versatile";

    @NotNull(message = "A origem do cálculo (IA ou fallback) é obrigatória.")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origem_calculo", nullable = false, length = 30)
    private OrigemCalculoPreco origemCalculo = OrigemCalculoPreco.IA_GENERATIVA;

    @Column(name = "data_geracao", nullable = false, updatable = false)
    private LocalDateTime dataGeracao;

    @PrePersist
    protected void aoPersistir() {
        this.dataGeracao = LocalDateTime.now();
    }
}