package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class DeathNotice {

    public enum Status {
        PENDING,
        SENT
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "death_notice_status", length = 20)
    private Status status;

    @Column(name = "death_notice_detail", length = 300)
    private String detail;

    @Column(name = "death_notice_at")
    private Instant at;

    protected DeathNotice() {
    }

    static DeathNotice pending(String detail, Instant at) {
        DeathNotice notice = new DeathNotice();
        notice.status = Status.PENDING;
        notice.detail = DomainRules.requiredText(detail, "detail", 300);
        notice.at = DomainRules.required(at, "at");
        return notice;
    }

    static DeathNotice sent(Instant at) {
        DeathNotice notice = new DeathNotice();
        notice.status = Status.SENT;
        notice.at = DomainRules.required(at, "at");
        return notice;
    }

    public boolean pending() {
        return status == Status.PENDING;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public Instant at() {
        return at;
    }
}
