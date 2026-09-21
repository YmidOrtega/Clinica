package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.UUID;

@Embeddable
public class Triage {

    public enum Level {
        I,
        II,
        III,
        IV,
        V
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_level", length = 3)
    private Level level;

    @Column(name = "triage_at")
    private Instant at;

    @Column(name = "triage_by_uuid")
    private UUID byUuid;

    protected Triage() {
    }

    static Triage classified(Level level, Instant at, UUID byUuid) {
        Triage triage = new Triage();
        triage.level = DomainRules.required(level, "level");
        triage.at = DomainRules.required(at, "at");
        triage.byUuid = byUuid;
        return triage;
    }

    public boolean newerThan(Triage other) {
        return other == null || other.at == null || at.isAfter(other.at);
    }

    public Level level() {
        return level;
    }

    public Instant at() {
        return at;
    }

    public UUID byUuid() {
        return byUuid;
    }
}
