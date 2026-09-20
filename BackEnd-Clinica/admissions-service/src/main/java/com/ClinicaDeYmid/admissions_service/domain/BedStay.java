package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bed_stays")
public class BedStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bed_id", nullable = false, updatable = false)
    private Bed bed;

    @Column(name = "occupant_uuid", nullable = false, updatable = false)
    private UUID occupant;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected BedStay() {
    }

    public static BedStay begin(Bed bed, UUID occupant, Instant startedAt) {
        BedStay stay = new BedStay();
        stay.uuid = UUID.randomUUID();
        stay.bed = DomainRules.required(bed, "bed");
        stay.occupant = DomainRules.required(occupant, "occupant");
        stay.startedAt = DomainRules.required(startedAt, "startedAt");
        return stay;
    }

    public void end(Clock clock) {
        if (endedAt != null) {
            throw new AdmissionsException.BedNotOccupied();
        }
        Instant now = Instant.now(clock);
        this.endedAt = now.isBefore(startedAt) ? startedAt : now;
    }

    public boolean open() {
        return endedAt == null;
    }

    public UUID uuid() {
        return uuid;
    }

    public Bed bed() {
        return bed;
    }

    public UUID occupant() {
        return occupant;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }
}
