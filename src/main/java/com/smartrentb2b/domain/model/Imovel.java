package com.smartrentb2b.domain.model;

import com.smartrentb2b.domain.model.enums.TipoImovel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Propriedade anunciada por um {@link Usuario} (anfitrião). Concentra os
 * atributos consumidos pelo motor preditivo de precificação: tipo, perfil
 * (capacidade/quartos/banheiros) e a microgeografia embutida em
 * {@link Endereco}.
 */
@Entity
@Table(name = "imoveis", indexes = {
        @Index(name = "idx_imoveis_usuario", columnList = "usuario_id"),
        @Index(name = "idx_imoveis_ativo", columnList = "ativo")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"usuario", "reservas", "sugestoesPreco"})
public class Imovel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "O imóvel deve estar vinculado a um anfitrião.")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_imovel_usuario"))
    private Usuario usuario;

    @NotBlank(message = "O título é obrigatório.")
    @Size(max = 150)
    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres.")
    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @NotNull(message = "O tipo de imóvel é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_imovel", nullable = false, length = 20)
    private TipoImovel tipoImovel;

    @NotNull(message = "O endereço é obrigatório.")
    @Valid
    @Embedded
    private Endereco endereco;

    @NotNull(message = "A capacidade de hóspedes é obrigatória.")
    @Min(value = 1, message = "A capacidade mínima é de 1 hóspede.")
    @Max(value = 50, message = "Capacidade acima do esperado para o MVP.")
    @Column(name = "capacidade_hospedes", nullable = false)
    private Integer capacidadeHospedes;

    @NotNull(message = "O número de quartos é obrigatório.")
    @Min(value = 0)
    @Column(name = "numero_quartos", nullable = false)
    private Integer numeroQuartos;

    @NotNull(message = "O número de banheiros é obrigatório.")
    @Min(value = 0)
    @Column(name = "numero_banheiros", nullable = false)
    private Integer numeroBanheiros;

    @NotNull(message = "O valor base da diária é obrigatório.")
    @DecimalMin(value = "0.01", message = "O valor da diária deve ser maior que zero.")
    @Digits(integer = 8, fraction = 2, message = "Valor de diária inválido.")
    @Column(name = "valor_diaria_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorDiariaBase;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "imovel_comodidades", joinColumns = @JoinColumn(name = "imovel_id"))
    @Column(name = "comodidade", length = 60)
    private List<String> comodidades = new ArrayList<>();

    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Builder.Default
    @OneToMany(mappedBy = "imovel", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Reserva> reservas = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "imovel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SugestaoPreco> sugestoesPreco = new ArrayList<>();

    @PrePersist
    protected void aoPersistir() {
        this.dataCadastro = LocalDateTime.now();
    }
}