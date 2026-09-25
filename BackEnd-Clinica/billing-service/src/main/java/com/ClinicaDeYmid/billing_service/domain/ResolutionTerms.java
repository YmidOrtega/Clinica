package com.ClinicaDeYmid.billing_service.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public record ResolutionTerms(
        String resolutionNumber,
        LocalDate issuedOn,
        String prefix,
        long rangeFrom,
        long rangeTo,
        LocalDate validFrom,
        LocalDate validUntil,
        String technicalKey) {

    private static final Pattern RESOLUTION_NUMBER = Pattern.compile("^[0-9]{1,20}$");
    private static final Pattern PREFIX = Pattern.compile("^[A-Z0-9]{0,4}$");
    private static final Pattern TECHNICAL_KEY = Pattern.compile("^[0-9a-f]{20,100}$");

    public ResolutionTerms {
        resolutionNumber = DomainRules.requiredPattern(resolutionNumber, "resolutionNumber", RESOLUTION_NUMBER,
                "debe ser el número de la resolución de la DIAN, solo dígitos");
        DomainRules.required(issuedOn, "issuedOn");
        prefix = DomainRules.requiredPattern(prefix == null ? "" : prefix.strip().toUpperCase(Locale.ROOT), "prefix",
                PREFIX, "debe tener hasta 4 letras o dígitos");
        if (rangeFrom < 1) {
            throw new BillingException.InvalidData("rangeFrom", "debe ser mayor que cero");
        }
        if (rangeTo < rangeFrom) {
            throw new BillingException.InvalidData("rangeTo", "no puede ser menor que el inicio del rango");
        }
        DomainRules.required(validFrom, "validFrom");
        DomainRules.required(validUntil, "validUntil");
        if (validUntil.isBefore(validFrom)) {
            throw new BillingException.InvalidData("validUntil", "no puede ser anterior al inicio de la vigencia");
        }
        if (validFrom.isBefore(issuedOn)) {
            throw new BillingException.InvalidData("validFrom", "no puede ser anterior a la fecha de la resolución");
        }
        technicalKey = DomainRules.requiredPattern(technicalKey == null ? null : technicalKey.strip().toLowerCase(Locale.ROOT),
                "technicalKey", TECHNICAL_KEY, "debe ser la clave técnica hexadecimal que entrega la DIAN");
    }

    public boolean overlaps(ResolutionTerms other) {
        return prefix.equals(other.prefix) && rangeFrom <= other.rangeTo && other.rangeFrom <= rangeTo;
    }
}
