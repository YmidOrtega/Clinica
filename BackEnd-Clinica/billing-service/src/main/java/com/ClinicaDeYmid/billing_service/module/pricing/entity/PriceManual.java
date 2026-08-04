package com.ClinicaDeYmid.billing_service.module.pricing.entity;

import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_manual",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_price_manual_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_price_manual_type", columnList = "type"),
                @Index(name = "idx_price_manual_active", columnList = "active"),
                @Index(name = "idx_price_manual_year", columnList = "year")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PriceManual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PriceManualType type;

    @Column(name = "year")
    private Short year;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "priceManual", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<PriceManualItem> items = new ArrayList<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
