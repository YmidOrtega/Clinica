package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;

@Entity
@Table(name = "specialties")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Specialty {

    public static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,19}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogueStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @NotAudited
    @OneToMany(mappedBy = "specialty", cascade = CascadeType.PERSIST)
    @OrderBy("code")
    private List<SubSpecialty> subSpecialties = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    protected Specialty() {
    }

    public static Specialty register(String code, String name) {
        Specialty specialty = new Specialty();
        specialty.uuid = UUID.randomUUID();
        specialty.code = Rules.matching(Rules.upper(Rules.requiredText(code, "code", 20)), CODE, "code");
        specialty.name = Rules.atLeast(Rules.requiredText(name, "name", 150), 3, "name");
        specialty.statusCode = CatalogueStatus.Code.ACTIVE;
        return specialty;
    }

    public boolean rename(String newName) {
        String renamed = Rules.atLeast(Rules.requiredText(newName, "name", 150), 3, "name");
        if (renamed.equals(name)) {
            return false;
        }
        name = renamed;
        return true;
    }

    public void deactivate(String reason, Clock clock) {
        apply(status().deactivate(reason, Instant.now(clock)));
        subSpecialties.stream()
                .filter(subSpecialty -> subSpecialty.status().active())
                .forEach(subSpecialty -> subSpecialty.deactivate(reason, clock));
    }

    public void reactivate() {
        apply(status().reactivate());
    }

    public SubSpecialty add(String subSpecialtyCode, String subSpecialtyName) {
        SubSpecialty subSpecialty = SubSpecialty.of(this, subSpecialtyCode, subSpecialtyName);
        subSpecialties.add(subSpecialty);
        return subSpecialty;
    }

    public CatalogueStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new CatalogueStatus.Active();
            case INACTIVE -> new CatalogueStatus.Inactive(statusReason, statusChangedAt);
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public List<SubSpecialty> subSpecialties() {
        return List.copyOf(subSpecialties);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void apply(CatalogueStatus status) {
        statusCode = status.code();
        statusReason = status instanceof CatalogueStatus.Inactive inactive ? inactive.reason() : null;
        statusChangedAt = status instanceof CatalogueStatus.Inactive inactive ? inactive.since() : null;
    }
}
