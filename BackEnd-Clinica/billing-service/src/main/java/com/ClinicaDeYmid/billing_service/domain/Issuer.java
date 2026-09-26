package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "issuer")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Issuer {

    public static final String DEFAULT_CREDIT_NOTE_PREFIX = "NC";
    private static final Pattern CREDIT_NOTE_PREFIX = Pattern.compile("^[A-Z0-9]{1,4}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "nit", nullable = false, updatable = false, length = 10)
    private String nit;

    @Column(name = "verification_digit", nullable = false, updatable = false)
    private int verificationDigit;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 20)
    private PersonType personType;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_scheme", nullable = false, length = 20)
    private TaxScheme taxScheme;

    @Convert(converter = TaxResponsibilitiesConverter.class)
    @Column(name = "tax_responsibilities", nullable = false, length = 60)
    private Set<TaxResponsibility> taxResponsibilities;

    @Column(name = "address_line", nullable = false, length = 200)
    private String addressLine;

    @Column(name = "municipality_code", nullable = false, length = 5)
    private String municipalityCode;

    @Column(name = "city_name", nullable = false, length = 60)
    private String cityName;

    @Column(name = "department_name", nullable = false, length = 60)
    private String departmentName;

    @Column(name = "postal_code", length = 6)
    private String postalCode;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 10)
    private String phone;

    @Column(name = "health_provider_code", nullable = false, length = 12)
    private String healthProviderCode;

    @Column(name = "credit_note_prefix", nullable = false, length = 4)
    private String creditNotePrefix = DEFAULT_CREDIT_NOTE_PREFIX;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private DianEnvironment environment;

    @Column(name = "production_since")
    private Instant productionSince;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    protected Issuer() {
    }

    public static Issuer configure(Nit nit, IssuerProfile profile) {
        Issuer issuer = new Issuer();
        issuer.uuid = UUID.randomUUID();
        issuer.nit = DomainRules.required(nit, "nit").number();
        issuer.verificationDigit = nit.verificationDigit();
        issuer.environment = DianEnvironment.TEST;
        issuer.apply(DomainRules.required(profile, "profile"));
        return issuer;
    }

    public boolean revise(IssuerProfile profile) {
        if (DomainRules.required(profile, "profile").equals(profile())) {
            return false;
        }
        apply(profile);
        return true;
    }

    public boolean useCreditNotePrefix(String prefix) {
        String accepted = DomainRules.requiredPattern(prefix, "prefix", CREDIT_NOTE_PREFIX,
                "debe tener de 1 a 4 letras mayúsculas o dígitos");
        if (accepted.equals(creditNotePrefix)) {
            return false;
        }
        creditNotePrefix = accepted;
        return true;
    }

    public String creditNotePrefix() {
        return creditNotePrefix;
    }

    public void goToProduction(Clock clock) {
        if (environment == DianEnvironment.PRODUCTION) {
            throw new BillingException.AlreadyInProduction();
        }
        environment = DianEnvironment.PRODUCTION;
        productionSince = Instant.now(clock);
    }

    private void apply(IssuerProfile profile) {
        personType = profile.personType();
        legalName = profile.legalName();
        tradeName = profile.tradeName();
        taxScheme = profile.taxScheme();
        taxResponsibilities = profile.taxResponsibilities();
        addressLine = profile.addressLine();
        municipalityCode = profile.municipalityCode();
        cityName = profile.cityName();
        departmentName = profile.departmentName();
        postalCode = profile.postalCode();
        email = profile.email();
        phone = profile.phone();
        healthProviderCode = profile.healthProviderCode();
    }

    public IssuerProfile profile() {
        return new IssuerProfile(personType, legalName, tradeName, taxScheme, taxResponsibilities, addressLine,
                municipalityCode, cityName, departmentName, postalCode, email, phone, healthProviderCode);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public Nit nit() {
        return new Nit(nit, verificationDigit);
    }

    public DianEnvironment environment() {
        return environment;
    }

    public Instant productionSince() {
        return productionSince;
    }
}
