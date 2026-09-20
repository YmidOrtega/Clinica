package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.UUID;

@Embeddable
public class Coverage {

    public enum Code {
        COVERED,
        NOT_COVERED,
        UNKNOWN
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_status", length = 20)
    private Code status;

    @Column(name = "coverage_contract_uuid")
    private UUID contractUuid;

    @Column(name = "coverage_contract_number", length = 60)
    private String contractNumber;

    @Column(name = "coverage_payer_uuid")
    private UUID payerUuid;

    @Column(name = "coverage_detail", length = 300)
    private String detail;

    @Column(name = "coverage_checked_at")
    private Instant checkedAt;

    protected Coverage() {
    }

    public static Coverage covered(UUID contractUuid, String contractNumber, UUID payerUuid, Instant at) {
        Coverage coverage = new Coverage();
        coverage.status = Code.COVERED;
        coverage.contractUuid = DomainRules.required(contractUuid, "contractUuid");
        coverage.contractNumber = DomainRules.requiredText(contractNumber, "contractNumber", 60);
        coverage.payerUuid = payerUuid;
        coverage.checkedAt = DomainRules.required(at, "at");
        return coverage;
    }

    public static Coverage notCovered(String detail, UUID payerUuid, Instant at) {
        Coverage coverage = new Coverage();
        coverage.status = Code.NOT_COVERED;
        coverage.detail = DomainRules.requiredText(detail, "detail", 300);
        coverage.payerUuid = payerUuid;
        coverage.checkedAt = DomainRules.required(at, "at");
        return coverage;
    }

    public static Coverage unknown(String detail, UUID payerUuid, Instant at) {
        Coverage coverage = new Coverage();
        coverage.status = Code.UNKNOWN;
        coverage.detail = DomainRules.requiredText(detail, "detail", 300);
        coverage.payerUuid = payerUuid;
        coverage.checkedAt = DomainRules.required(at, "at");
        return coverage;
    }

    public boolean settled() {
        return status == Code.COVERED;
    }

    public boolean pending() {
        return status != null && status != Code.COVERED;
    }

    public Code status() {
        return status;
    }

    public UUID contractUuid() {
        return contractUuid;
    }

    public String contractNumber() {
        return contractNumber;
    }

    public UUID payerUuid() {
        return payerUuid;
    }

    public String detail() {
        return detail;
    }

    public Instant checkedAt() {
        return checkedAt;
    }
}
