package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "configuration_services")
@Audited
@AuditTable(value = "configuration_services_aud", schema = "admissions_history")
@EntityListeners(AuditingEntityListener.class)
public class ConfigurationService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false, updatable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogueStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

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

    protected ConfigurationService() {
    }

    public static ConfigurationService configure(ServiceType serviceType, Location location) {
        ConfigurationService configured = new ConfigurationService();
        configured.uuid = UUID.randomUUID();
        configured.serviceType = DomainRules.required(serviceType, "serviceType");
        configured.location = DomainRules.required(location, "location");
        if (!serviceType.status().usable()) {
            throw new AdmissionsException.RetiredServiceType();
        }
        if (!location.status().usable()) {
            throw new AdmissionsException.RetiredLocation();
        }
        configured.statusCode = CatalogueStatus.Code.ACTIVE;
        return configured;
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public Location location() {
        return location;
    }

    public AdmissionKind kind() {
        return serviceType.kind();
    }

    public String name() {
        return serviceType.name() + " - " + location.name();
    }

    public void retire(String reason, Clock clock) {
        applyStatus(status().retire(reason, Instant.now(clock)));
    }

    public void restore() {
        applyStatus(status().restore());
    }

    public CatalogueStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new CatalogueStatus.Active();
            case RETIRED -> new CatalogueStatus.Retired(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(CatalogueStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case CatalogueStatus.Active ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
            }
            case CatalogueStatus.Retired retired -> {
                this.statusReason = retired.reason();
                this.statusChangedAt = retired.since();
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }
}
