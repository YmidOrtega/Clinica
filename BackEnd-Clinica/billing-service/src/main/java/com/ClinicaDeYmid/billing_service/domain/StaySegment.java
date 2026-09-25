package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stay_segments")
@Audited
public class StaySegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private EpisodeAccount account;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "bed_uuid", nullable = false, updatable = false, length = 36)
    private UUID bedUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_type", updatable = false, length = 30)
    private StayType stayType;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected StaySegment() {
    }

    static StaySegment begin(EpisodeAccount account, UUID bedUuid, StayType stayType, Instant at) {
        StaySegment segment = new StaySegment();
        segment.account = account;
        segment.bedUuid = DomainRules.required(bedUuid, "bedUuid");
        segment.stayType = stayType;
        segment.startedAt = DomainRules.required(at, "startedAt");
        return segment;
    }

    void end(Instant at) {
        endedAt = at.isBefore(startedAt) ? startedAt : at;
    }

    public boolean open() {
        return endedAt == null;
    }

    public UUID bedUuid() {
        return bedUuid;
    }

    public StayType stayType() {
        return stayType;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }
}
