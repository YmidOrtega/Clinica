package com.ClinicaDeYmid.module.billing.model;

import com.ClinicaDeYmid.module.billing.model.emun.AttentionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "type_of_attention", indexes = {
        @Index(name = "idx_attention_type_name", columnList = "name"),
        @Index(name = "idx_attention_type_code", columnList = "code")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Builder
public class TypeOfAttention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del tipo de atención es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @NotBlank(message = "El código es obligatorio")
    @Size(min = 1, max = 10, message = "El código debe tener entre 1 y 10 caracteres")
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_category", length = 50)
    private AttentionType attentionCategory;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    // Relación con atenciones
    @OneToMany(mappedBy = "typeOfAttention", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Attention> attentions = new ArrayList<>();

    public TypeOfAttention(String name, String code, String description, AttentionType attentionCategory) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.attentionCategory = attentionCategory;
        this.active = true;
    }
}