package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingStatus;
import com.ClinicaDeYmid.ai_assistant_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding {

    public static final String WHOLE_INVOICE = "-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "invoice_uuid", nullable = false, updatable = false)
    private UUID invoiceUuid;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 24)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule", nullable = false, updatable = false, length = 40)
    private FindingRule rule;

    @Column(name = "subject", nullable = false, updatable = false, length = 40)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 10)
    private FindingRule.Severity severity;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FindingStatus status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Finding() {
    }

    public static Finding open(UUID invoiceUuid, String invoiceNumber, FindingRule rule, String subject, String detail,
                               LocalDate dueOn, Instant at) {
        Finding finding = new Finding();
        finding.uuid = UUID.randomUUID();
        finding.invoiceUuid = Rules.required(invoiceUuid, "invoiceUuid");
        finding.invoiceNumber = Rules.requiredText(invoiceNumber, "invoiceNumber", 24);
        finding.rule = Rules.required(rule, "rule");
        finding.subject = subject == null ? WHOLE_INVOICE : subject;
        finding.severity = rule.severity();
        finding.detail = Rules.requiredText(detail, "detail", 500);
        finding.dueOn = dueOn;
        finding.status = FindingStatus.OPEN;
        finding.detectedAt = at;
        return finding;
    }

    public boolean restate(String newDetail, LocalDate newDueOn) {
        String text = Rules.requiredText(newDetail, "detail", 500);
        if (text.equals(detail) && java.util.Objects.equals(newDueOn, dueOn)) {
            return false;
        }
        detail = text;
        dueOn = newDueOn;
        return true;
    }

    public void resolve(Instant at) {
        if (status == FindingStatus.OPEN) {
            status = FindingStatus.RESOLVED;
            resolvedAt = at;
        }
    }

    public boolean open() {
        return status == FindingStatus.OPEN;
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public UUID invoiceUuid() {
        return invoiceUuid;
    }

    public String invoiceNumber() {
        return invoiceNumber;
    }

    public FindingRule rule() {
        return rule;
    }

    public String subject() {
        return subject;
    }

    public FindingRule.Severity severity() {
        return severity;
    }

    public String detail() {
        return detail;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public FindingStatus status() {
        return status;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }
}
