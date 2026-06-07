package com.ClinicaDeYmid.billing_service.module.config.entity;

import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dian_resolution",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dian_resolution_number", columnNames = "resolution_number"),
                @UniqueConstraint(name = "uk_dian_resolution_active_type", columnNames = {"document_type", "active"})
        },
        indexes = {
                @Index(name = "idx_dian_resolution_document_type", columnList = "document_type"),
                @Index(name = "idx_dian_resolution_active", columnList = "active"),
                @Index(name = "idx_dian_resolution_valid_to", columnList = "valid_to")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DianResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resolution_number", nullable = false, length = 50)
    private String resolutionNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "prefix", length = 10)
    private String prefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "from_number", nullable = false)
    private Long fromNumber;

    @Column(name = "to_number", nullable = false)
    private Long toNumber;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "current_consecutive", nullable = false)
    @Builder.Default
    private Long currentConsecutive = 1L;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isExpired() {
        return LocalDate.now().isAfter(validTo);
    }

    public boolean isExhausted() {
        return currentConsecutive > toNumber;
    }

    public boolean isValid() {
        return active && !isExpired() && !isExhausted();
    }
}
