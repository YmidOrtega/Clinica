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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "practitioner_fees")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class PractitionerFee {

    public enum Status {
        PAYABLE,
        UNAGREED,
        VOIDED
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

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_line_uuid", nullable = false, updatable = false, length = 36)
    private UUID saleLineUuid;

    @Column(name = "cups_code", nullable = false, updatable = false, length = 8)
    private String cupsCode;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 20)
    private SurgicalRole role;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "practitioner_uuid", nullable = false, updatable = false, length = 36)
    private UUID practitionerUuid;

    @Column(name = "practitioner_name", nullable = false, updatable = false, length = 200)
    private String practitionerName;

    @Column(name = "performed_on", nullable = false, updatable = false)
    private LocalDate performedOn;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "agreement_uuid", updatable = false, length = 36)
    private UUID agreementUuid;

    @Column(name = "amount", updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PractitionerFee() {
    }

    public static List<PractitionerFee> owedFor(Sale sale, Map<UUID, Optional<FeeAgreementTerms>> agreements) {
        if (!(sale.type() instanceof SaleType.Surgical surgical)) {
            return List.of();
        }
        if (!(sale.status() instanceof SaleStatus.Confirmed)) {
            throw new IllegalStateException("Fees are only owed for a confirmed sale");
        }
        List<PractitionerFee> fees = new ArrayList<>();
        for (SaleLine line : sale.activeLines()) {
            Optional<SurgicalDetail> detail = line.price().map(LinePrice::surgical);
            if (detail.isEmpty()) {
                continue;
            }
            for (ComponentCharge charge : detail.get().components()) {
                if (charge.amount().signum() <= 0 || charge.component().performedBy().isEmpty()) {
                    continue;
                }
                SurgicalRole role = charge.component().performedBy().get();
                sale.surgicalTeam().stream().filter(member -> member.role() == role).findFirst().ifPresent(member ->
                        fees.add(owed(sale, line, member, surgical.performedOn(),
                                agreements.getOrDefault(member.practitionerUuid(), Optional.empty()))));
            }
        }
        return fees;
    }

    private static PractitionerFee owed(Sale sale, SaleLine line, TeamMember member, LocalDate performedOn,
                                        Optional<FeeAgreementTerms> agreement) {
        PractitionerFee fee = new PractitionerFee();
        fee.uuid = UUID.randomUUID();
        fee.sale = sale;
        fee.saleLineUuid = line.uuid();
        fee.cupsCode = line.service().cupsCode();
        fee.description = line.service().description();
        fee.role = member.role();
        fee.practitionerUuid = member.practitionerUuid();
        fee.practitionerName = member.fullName();
        fee.performedOn = performedOn;
        if (agreement.isEmpty()) {
            fee.status = Status.UNAGREED;
            fee.statusReason = "El profesional no tiene honorarios pactados vigentes en la fecha de la cirugía";
            return fee;
        }
        fee.agreementUuid = agreement.get().agreementUuid();
        if (!FeeAgreementTerms.PER_PROCEDURE.equals(agreement.get().basis())) {
            fee.status = Status.UNAGREED;
            fee.statusReason = "El acuerdo vigente es " + agreement.get().basis() + ", no por procedimiento";
            return fee;
        }
        BigDecimal agreed = agreement.get().perProcedure().get(fee.cupsCode);
        if (agreed == null) {
            fee.status = Status.UNAGREED;
            fee.statusReason = "El acuerdo por procedimiento no incluye " + fee.cupsCode;
            return fee;
        }
        fee.amount = Money.of(agreed);
        fee.status = Status.PAYABLE;
        return fee;
    }

    public void voidBecause(String reason) {
        if (status == Status.VOIDED) {
            return;
        }
        status = Status.VOIDED;
        statusReason = DomainRules.requiredText(reason, "reason", 500);
    }

    public UUID uuid() {
        return uuid;
    }

    public Sale sale() {
        return sale;
    }

    public UUID saleLineUuid() {
        return saleLineUuid;
    }

    public String cupsCode() {
        return cupsCode;
    }

    public String description() {
        return description;
    }

    public SurgicalRole role() {
        return role;
    }

    public UUID practitionerUuid() {
        return practitionerUuid;
    }

    public String practitionerName() {
        return practitionerName;
    }

    public LocalDate performedOn() {
        return performedOn;
    }

    public UUID agreementUuid() {
        return agreementUuid;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Status status() {
        return status;
    }

    public String statusReason() {
        return statusReason;
    }
}
