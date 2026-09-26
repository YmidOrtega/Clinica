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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "dian_verdicts")
public class DianVerdict {

    public enum Operation {
        SEND_TEST_SET,
        SEND_BILL,
        STATUS_OF_ZIP,
        STATUS_OF_DOCUMENT
    }

    public enum Outcome {
        RECEIVED,
        PROCESSING,
        ACCEPTED,
        REJECTED
    }

    private static final String SEPARATOR = "\n";
    private static final int MAX_ERRORS = 60_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "electronic_document_id", nullable = false, updatable = false)
    private ElectronicDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, updatable = false, length = 30)
    private Operation operation;

    @Column(name = "file_name", updatable = false, length = 60)
    private String fileName;

    @Column(name = "track_id", updatable = false, length = 100)
    private String trackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private Outcome outcome;

    @Column(name = "status_code", updatable = false, length = 10)
    private String statusCode;

    @Column(name = "status_description", updatable = false, length = 500)
    private String statusDescription;

    @Column(name = "errors", updatable = false, columnDefinition = "TEXT")
    private String errors;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected DianVerdict() {
    }

    public static DianVerdict of(ElectronicDocument document, Operation operation, Outcome outcome, String statusCode,
                                 String statusDescription, List<String> errors, Instant receivedAt) {
        DianVerdict verdict = new DianVerdict();
        verdict.document = DomainRules.required(document, "document");
        verdict.operation = DomainRules.required(operation, "operation");
        verdict.outcome = DomainRules.required(outcome, "outcome");
        verdict.fileName = document.dianFileName();
        verdict.trackId = document.dianTrackId();
        verdict.statusCode = truncated(statusCode, 10);
        verdict.statusDescription = truncated(statusDescription, 500);
        verdict.errors = errors == null || errors.isEmpty() ? null
                : truncated(String.join(SEPARATOR, errors), MAX_ERRORS);
        verdict.receivedAt = DomainRules.required(receivedAt, "receivedAt");
        return verdict;
    }

    private static String truncated(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public Operation operation() {
        return operation;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String fileName() {
        return fileName;
    }

    public String trackId() {
        return trackId;
    }

    public String statusCode() {
        return statusCode;
    }

    public String statusDescription() {
        return statusDescription;
    }

    public List<String> errors() {
        return errors == null ? List.of() : Arrays.asList(errors.split(SEPARATOR));
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
