package com.ClinicaDeYmid.contracting_service.domain;

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
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "capitated_members")
public class CapitatedMember {

    private static final Pattern DOCUMENT_TYPE = Pattern.compile("^[A-Z]{2,20}$");
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("^[A-Z0-9-]{3,20}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @Column(name = "period", nullable = false, updatable = false)
    private LocalDate period;

    @Column(name = "document_type", nullable = false, updatable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, updatable = false, length = 20)
    private String documentNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "patient_uuid", length = 36)
    private UUID patientUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification", nullable = false, length = 20)
    private MemberVerification verification;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", updatable = false, length = 36)
    private String registeredBy;

    protected CapitatedMember() {
    }

    public static CapitatedMember of(Contract contract, YearMonth period, String documentType, String documentNumber,
                                     String fullName, String registeredBy, Clock clock) {
        CapitatedMember member = new CapitatedMember();
        member.uuid = UUID.randomUUID();
        member.contract = DomainRules.required(contract, "contract");
        if (contract.modality() != ContractModality.CAPITATION) {
            throw new ContractingException.CapitationNotApplicable(contract.modality());
        }
        member.period = DomainRules.required(period, "period").atDay(1);
        member.documentType = DomainRules.matching(
                DomainRules.upper(DomainRules.requiredText(documentType, "documentType", 20)), DOCUMENT_TYPE, "documentType");
        member.documentNumber = DomainRules.matching(
                DomainRules.upper(DomainRules.requiredText(documentNumber, "documentNumber", 20)), DOCUMENT_NUMBER, "documentNumber");
        member.fullName = DomainRules.requiredText(fullName, "fullName", 200);
        member.verification = MemberVerification.UNVERIFIED;
        member.registeredAt = Instant.now(clock);
        member.registeredBy = registeredBy;
        return member;
    }

    public void matched(UUID patient, Clock clock) {
        patientUuid = DomainRules.required(patient, "patientUuid");
        verification = MemberVerification.MATCHED;
        verifiedAt = Instant.now(clock);
    }

    public void unmatched(Clock clock) {
        patientUuid = null;
        verification = MemberVerification.UNMATCHED;
        verifiedAt = Instant.now(clock);
    }

    public void unverified() {
        verification = MemberVerification.UNVERIFIED;
        verifiedAt = null;
    }

    public boolean rename(String newName) {
        String name = DomainRules.requiredText(newName, "fullName", 200);
        if (name.equals(fullName)) {
            return false;
        }
        fullName = name;
        return true;
    }

    public UUID uuid() {
        return uuid;
    }

    public Contract contract() {
        return contract;
    }

    public YearMonth period() {
        return YearMonth.from(period);
    }

    public String documentType() {
        return documentType;
    }

    public String documentNumber() {
        return documentNumber;
    }

    public String fullName() {
        return fullName;
    }

    public UUID patientUuid() {
        return patientUuid;
    }

    public MemberVerification verification() {
        return verification;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }
}
