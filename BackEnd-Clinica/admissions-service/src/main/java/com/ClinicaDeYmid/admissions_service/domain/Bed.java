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
@Table(name = "beds")
@Audited
@AuditTable(value = "beds_aud", schema = "admissions_history")
@EntityListeners(AuditingEntityListener.class)
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BedStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "occupant_uuid")
    private UUID occupant;

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

    protected Bed() {
    }

    public static Bed install(String label, Room room) {
        Bed bed = new Bed();
        bed.uuid = UUID.randomUUID();
        bed.label = DomainRules.requiredText(label, "label", 30);
        bed.room = DomainRules.required(room, "room");
        if (!room.status().usable()) {
            throw new AdmissionsException.RetiredRoom();
        }
        bed.statusCode = BedStatus.Code.AVAILABLE;
        return bed;
    }

    public boolean relabel(String newLabel) {
        String label = DomainRules.requiredText(newLabel, "label", 30);
        if (label.equals(this.label)) {
            return false;
        }
        this.label = label;
        return true;
    }

    public void occupy(UUID occupant, Clock clock) {
        applyStatus(status().occupy(occupant, Instant.now(clock)));
    }

    public void release(Clock clock) {
        applyStatus(status().release(Instant.now(clock)));
    }

    public void finishCleaning() {
        applyStatus(status().finishCleaning());
    }

    public void sendToMaintenance(String reason, Clock clock) {
        applyStatus(status().sendToMaintenance(reason, Instant.now(clock)));
    }

    public void block(String reason, Clock clock) {
        applyStatus(status().block(reason, Instant.now(clock)));
    }

    public void returnToService() {
        applyStatus(status().returnToService());
    }

    public BedStatus status() {
        return switch (statusCode) {
            case AVAILABLE -> new BedStatus.Available();
            case OCCUPIED -> new BedStatus.Occupied(occupant, statusChangedAt);
            case CLEANING -> new BedStatus.Cleaning(statusChangedAt);
            case MAINTENANCE -> new BedStatus.Maintenance(statusReason, statusChangedAt);
            case BLOCKED -> new BedStatus.Blocked(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(BedStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case BedStatus.Available ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
                this.occupant = null;
            }
            case BedStatus.Occupied occupied -> {
                this.statusReason = null;
                this.statusChangedAt = occupied.since();
                this.occupant = occupied.occupant();
            }
            case BedStatus.Cleaning cleaning -> {
                this.statusReason = null;
                this.statusChangedAt = cleaning.since();
                this.occupant = null;
            }
            case BedStatus.Maintenance maintenance -> {
                this.statusReason = maintenance.reason();
                this.statusChangedAt = maintenance.since();
                this.occupant = null;
            }
            case BedStatus.Blocked blocked -> {
                this.statusReason = blocked.reason();
                this.statusChangedAt = blocked.since();
                this.occupant = null;
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String label() {
        return label;
    }

    public Room room() {
        return room;
    }
}
