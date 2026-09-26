package com.ClinicaDeYmid.billing_service.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "electronic_documents")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class ElectronicDocument {

    public enum Type {
        INVOICE("fv"),
        CREDIT_NOTE("nc");

        private final String filePrefix;

        Type(String filePrefix) {
            this.filePrefix = filePrefix;
        }

        public String filePrefix() {
            return filePrefix;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private Type type;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "number", nullable = false, updatable = false, length = 24)
    private String number;

    @Column(name = "document_key", nullable = false, updatable = false, length = 96)
    private String documentKey;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dian_status", length = 30)
    private DianStatus dianStatus;

    @Column(name = "dian_status_at")
    private Instant dianStatusAt;

    @Column(name = "dian_file_name", length = 60)
    private String dianFileName;

    @Column(name = "dian_track_id", length = 100)
    private String dianTrackId;

    @Column(name = "dian_attempts", nullable = false)
    private int dianAttempts;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ElectronicDocument() {
    }

    public static ElectronicDocument ofInvoice(Invoice invoice) {
        DomainRules.required(invoice, "invoice");
        if (!(invoice.status() instanceof InvoiceStatus.Issued issued) || invoice.cufe() == null) {
            throw new IllegalStateException("Only an issued and identified invoice is an electronic document");
        }
        return of(Type.INVOICE, invoice, invoice.number(), invoice.cufe(), issued.at());
    }

    static ElectronicDocument of(Type type, Invoice invoice, String number, String documentKey, Instant issuedAt) {
        ElectronicDocument document = new ElectronicDocument();
        document.uuid = UUID.randomUUID();
        document.type = type;
        document.invoice = invoice;
        document.number = DomainRules.requiredText(number, "number", 24);
        document.documentKey = DomainRules.requiredText(documentKey, "documentKey", 96);
        document.issuedAt = DomainRules.required(issuedAt, "issuedAt");
        return document;
    }

    public void sign(Instant at) {
        if (signedAt != null) {
            throw new IllegalStateException("An electronic document is signed once");
        }
        signedAt = DomainRules.required(at, "signedAt");
    }

    public void attemptDianDelivery(String fileName) {
        if (signedAt == null || dianStatus != null) {
            throw new BillingException.DocumentNotDeliverable(
                    "Solo se envía a la DIAN un documento firmado que no esté en validación, aceptado ni rechazado");
        }
        dianFileName = DomainRules.requiredText(fileName, "dianFileName", 60);
        dianTrackId = null;
        dianAttempts++;
    }

    public void awaitDianValidation(String trackId, Instant at) {
        requireDelivering();
        dianTrackId = DomainRules.requiredText(trackId, "dianTrackId", 100);
        dianStatus = DianStatus.AWAITING_VALIDATION;
        dianStatusAt = DomainRules.required(at, "at");
    }

    public void acceptedByDian(Instant at) {
        requireDelivering();
        dianStatus = DianStatus.ACCEPTED;
        dianStatusAt = DomainRules.required(at, "at");
    }

    public void rejectedByDian(Instant at) {
        requireDelivering();
        dianStatus = DianStatus.REJECTED;
        dianStatusAt = DomainRules.required(at, "at");
    }

    public void requeueForDian() {
        if (dianStatus != DianStatus.REJECTED) {
            throw new BillingException.DocumentNotDeliverable("Solo se reenvía a la DIAN un documento rechazado");
        }
        dianStatus = null;
        dianStatusAt = null;
        dianTrackId = null;
    }

    private void requireDelivering() {
        if (dianFileName == null || (dianStatus != null && dianStatus != DianStatus.AWAITING_VALIDATION)) {
            throw new IllegalStateException("The document is not being delivered to the DIAN");
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public Type type() {
        return type;
    }

    public Invoice invoice() {
        return invoice;
    }

    public String number() {
        return number;
    }

    public String documentKey() {
        return documentKey;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant signedAt() {
        return signedAt;
    }

    public DianStatus dianStatus() {
        return dianStatus;
    }

    public Instant dianStatusAt() {
        return dianStatusAt;
    }

    public String dianFileName() {
        return dianFileName;
    }

    public String dianTrackId() {
        return dianTrackId;
    }

    public int dianAttempts() {
        return dianAttempts;
    }
}
