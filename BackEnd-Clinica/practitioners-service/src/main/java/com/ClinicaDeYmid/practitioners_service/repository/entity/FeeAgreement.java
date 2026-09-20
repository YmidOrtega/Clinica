package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.FeeBasis;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fee_agreements")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class FeeAgreement {

    private static final BigDecimal MINIMUM = new BigDecimal("1000");
    private static final BigDecimal MAXIMUM = new BigDecimal("100000000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practitioner_id", nullable = false, updatable = false)
    private Practitioner practitioner;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, updatable = false, length = 20)
    private FeeBasis basis;

    @Column(name = "amount", precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "revoked_on")
    private LocalDate revokedOn;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    @NotAudited
    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL)
    @OrderBy("serviceCode")
    private List<FeeAgreementLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    protected FeeAgreement() {
    }

    public static FeeAgreement agree(Practitioner practitioner, FeeBasis basis, BigDecimal amount,
                                     List<Line> procedureLines, LocalDate validFrom, String note) {
        if (practitioner.status() instanceof PractitionerStatus.Retired) {
            throw new PractitionersException.FeesForRetiredPractitioner();
        }
        FeeAgreement agreement = new FeeAgreement();
        agreement.uuid = UUID.randomUUID();
        agreement.practitioner = practitioner;
        agreement.basis = Rules.required(basis, "basis");
        agreement.validFrom = Rules.required(validFrom, "validFrom");
        agreement.note = Rules.optionalText(note, "note", 500);
        if (basis == FeeBasis.PER_PROCEDURE) {
            agreement.perProcedure(procedureLines, amount);
        } else {
            agreement.flat(amount, procedureLines);
        }
        return agreement;
    }

    public void revokeFrom(LocalDate date) {
        if (!date.isAfter(validFrom)) {
            throw new PractitionersException.FeeAgreementOverlaps();
        }
        revokedOn = date;
    }

    public boolean inForceOn(LocalDate date) {
        return !date.isBefore(validFrom) && (revokedOn == null || date.isBefore(revokedOn));
    }

    public UUID uuid() {
        return uuid;
    }

    public Practitioner practitioner() {
        return practitioner;
    }

    public FeeBasis basis() {
        return basis;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate revokedOn() {
        return revokedOn;
    }

    public String note() {
        return note;
    }

    public List<FeeAgreementLine> lines() {
        return List.copyOf(lines);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    private void flat(BigDecimal value, List<Line> procedureLines) {
        if (procedureLines != null && !procedureLines.isEmpty()) {
            throw new PractitionersException.InvalidData("lines", "solo aplica a honorarios por procedimiento");
        }
        amount = money(value, "amount");
    }

    private void perProcedure(List<Line> procedureLines, BigDecimal flatAmount) {
        if (flatAmount != null) {
            throw new PractitionersException.InvalidData("amount", "no aplica a honorarios por procedimiento");
        }
        if (procedureLines == null || procedureLines.isEmpty()) {
            throw new PractitionersException.InvalidData("lines", "es obligatorio para honorarios por procedimiento");
        }
        procedureLines.forEach(line -> {
            FeeAgreementLine added = FeeAgreementLine.of(this, line.serviceCode(), money(line.amount(), "lines.amount"));
            if (lines.stream().anyMatch(existing -> existing.serviceCode().equals(added.serviceCode()))) {
                throw new PractitionersException.InvalidData("lines.serviceCode", "no puede repetirse en el acuerdo");
            }
            lines.add(added);
        });
    }

    private static BigDecimal money(BigDecimal value, String field) {
        BigDecimal money = Rules.required(value, field).setScale(2, RoundingMode.UNNECESSARY);
        if (money.compareTo(MINIMUM) < 0 || money.compareTo(MAXIMUM) > 0) {
            throw new PractitionersException.InvalidData(field, "debe estar entre 1000 y 100000000 pesos");
        }
        return money;
    }

    public record Line(String serviceCode, BigDecimal amount) {
    }
}
