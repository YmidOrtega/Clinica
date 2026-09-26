package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "rips_submissions")
@EntityListeners(AuditingEntityListener.class)
public class RipsSubmission {

    public enum Status {
        PENDING,
        VALIDATED,
        REJECTED
    }

    public static final Pattern CUV = Pattern.compile("[0-9a-fA-F]{96}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Lob
    @Column(name = "rips", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String rips;

    @Lob
    @Column(name = "response", columnDefinition = "MEDIUMTEXT")
    private String response;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "cuv", length = 96)
    private String cuv;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "recovered", nullable = false)
    private boolean recovered;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_failure", length = 500)
    private String lastFailure;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ElementCollection
    @CollectionTable(name = "rips_submission_findings", joinColumns = @JoinColumn(name = "submission_id"))
    @OrderColumn(name = "position")
    private List<MinistryFinding> findings = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RipsSubmission() {
    }

    public static RipsSubmission prepare(Invoice invoice, int sequence, String rips) {
        DomainRules.required(invoice, "invoice");
        if (invoice.purpose() != Invoice.Purpose.SERVICES) {
            throw new BillingException.RipsNotApplicable();
        }
        if (!(invoice.status() instanceof InvoiceStatus.Issued)) {
            throw new BillingException.InvoiceNotIssued();
        }
        RipsSubmission submission = new RipsSubmission();
        submission.uuid = UUID.randomUUID();
        submission.invoice = invoice;
        submission.sequence = sequence;
        submission.status = Status.PENDING;
        submission.rips = DomainRules.required(rips, "rips");
        return submission;
    }

    public void unreachable(String failure, Instant at) {
        requirePending();
        attempts++;
        lastAttemptAt = DomainRules.required(at, "at");
        lastFailure = failure == null ? null : failure.length() <= 500 ? failure : failure.substring(0, 500);
    }

    public void validated(Long processId, String cuv, Instant filedAt, boolean recovered, List<MinistryFinding> findings,
                          String response, Instant at) {
        requirePending();
        if (cuv == null || !CUV.matcher(cuv).matches()) {
            throw new IllegalArgumentException("A CUV has 96 hexadecimal characters");
        }
        this.cuv = cuv.toLowerCase(java.util.Locale.ROOT);
        this.processId = processId;
        this.filedAt = filedAt;
        this.recovered = recovered;
        resolve(Status.VALIDATED, findings, response, at);
    }

    public void rejected(Long processId, List<MinistryFinding> findings, String response, Instant at) {
        requirePending();
        this.processId = processId;
        resolve(Status.REJECTED, findings, response, at);
    }

    private void resolve(Status outcome, List<MinistryFinding> found, String answer, Instant at) {
        status = outcome;
        attempts++;
        lastAttemptAt = DomainRules.required(at, "at");
        resolvedAt = at;
        lastFailure = null;
        response = answer;
        findings.addAll(found);
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("The RIPS submission " + uuid + " already has the ministry's answer");
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public Invoice invoice() {
        return invoice;
    }

    public int sequence() {
        return sequence;
    }

    public Status status() {
        return status;
    }

    public String rips() {
        return rips;
    }

    public String response() {
        return response;
    }

    public Long processId() {
        return processId;
    }

    public String cuv() {
        return cuv;
    }

    public Instant filedAt() {
        return filedAt;
    }

    public boolean recovered() {
        return recovered;
    }

    public int attempts() {
        return attempts;
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public String lastFailure() {
        return lastFailure;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public List<MinistryFinding> findings() {
        return List.copyOf(findings);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }
}
