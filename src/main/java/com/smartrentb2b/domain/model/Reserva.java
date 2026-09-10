package com.smartrentb2b.domain.model;

import com.smartrentb2b.domain.model.enums.OrigemReserva;
import com.smartrentb2b.domain.model.enums.StatusReserva;
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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * Reserva de um {@link Imovel} em um intervalo [dataCheckin, dataCheckout).
 * <p>
 * A trava de conflito entre reservas do MESMO imóvel (requisito Must Have da
 * matriz MoSCoW) não pode ser expressa apenas com Bean Validation — depende
 * de uma consulta ao estado atual do banco — e por isso é implementada em
 * {@link com.smartrentb2b.repository.ReservaRepository}. Esta
 * entidade valida somente a coerência interna do período (checkout após
 * checkin) via {@link #isPeriodoValido()}.
 * <p>
 * O campo {@link #versao} habilita lock otimista (JPA {@code @Version}) como
 * salvaguarda adicional contra condições de corrida em reservas concorrentes
 * para o mesmo período, complementando a verificação de conflito na camada
 * de serviço.
 */
@Entity
@Table(name = "reservas", indexes = {
        @Index(name = "idx_reservas_imovel", columnList = "imovel_id"),
        @Index(name = "idx_reservas_periodo", columnList = "imovel_id, data_checkin, data_checkout")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "imovel")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "A reserva deve estar vinculada a um imóvel.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reserva_imovel"))
    private Imovel imovel;

    @NotBlank(message = "O nome do hóspede é obrigatório.")
    @Size(max = 120)
    @Column(name = "hospede_nome", nullable = false, length = 120)
    private String hospedeNome;

    @NotBlank(message = "O e-mail do hóspede é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 150)
    @Column(name = "hospede_email", nullable = false, length = 150)
    private String hospedeEmail;

    @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$", message = "Telefone inválido.")
    @Column(name = "hospede_telefone", length = 20)
    private String hospedeTelefone;

    @NotNull(message = "A data de check-in é obrigatória.")
    @Column(name = "data_checkin", nullable = false)
    private LocalDate dataCheckin;

    @NotNull(message = "A data de check-out é obrigatória.")
    @Column(name = "data_checkout", nullable = false)
    private LocalDate dataCheckout;

    @NotNull(message = "O valor total da reserva é obrigatório.")
    @DecimalMin(value = "0.0", inclusive = true, message = "O valor total não pode ser negativo.")
    @Digits(integer = 8, fraction = 2, message = "Valor total inválido.")
    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @NotNull(message = "O status da reserva é obrigatório.")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReserva status = StatusReserva.PENDENTE;

    @NotNull(message = "A origem da reserva é obrigatória.")
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemReserva origem = OrigemReserva.DIRETA;

    @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres.")
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Lock otimista — salvaguarda extra contra reservas concorrentes conflitantes. */
    @Version
    @Column(name = "versao")
    private Long versao;

    @PrePersist
    protected void aoPersistir() {
        this.dataCriacao = LocalDateTime.now();
    }

    /**
     * Validação de coerência de datas (checkout estritamente posterior ao
     * checkin). Marcada como {@code @Transient} para que o Hibernate não a
     * interprete como uma propriedade persistente (evita o problema comum do
     * prefixo "is" em getters booleanos de entidades JPA).
     */
    @AssertTrue(message = "A data de check-out deve ser posterior à data de check-in.")
    @Transient
    public boolean isPeriodoValido() {
        if (dataCheckin == null || dataCheckout == null) {
            return true; // @NotNull já cobre a ausência individual dos campos
        }
        return dataCheckout.isAfter(dataCheckin);
    }
}