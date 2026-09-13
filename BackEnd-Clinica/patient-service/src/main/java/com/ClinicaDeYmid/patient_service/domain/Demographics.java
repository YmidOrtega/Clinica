package com.ClinicaDeYmid.patient_service.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

public record Demographics(PersonName name, LocalDate birthDate, Sex sex, String countryOfOrigin, Disability disability) {

    static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    static final int ADULT_AGE = 18;

    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Demographics {
        DomainRules.required(name, "name");
        DomainRules.required(birthDate, "birthDate");
        DomainRules.required(sex, "sex");
        countryOfOrigin = DomainRules.matching(
                DomainRules.upper(DomainRules.requiredText(countryOfOrigin, "countryOfOrigin", 2)), COUNTRY, "countryOfOrigin");
        DomainRules.required(disability, "disability");
        if (birthDate.isBefore(EARLIEST_BIRTH_DATE)) {
            throw new PatientException.InvalidData("birthDate", "no puede ser anterior a " + EARLIEST_BIRTH_DATE);
        }
    }

    public int ageOn(LocalDate today) {
        if (birthDate.isAfter(today)) {
            throw new PatientException.InvalidData("birthDate", "no puede ser una fecha futura");
        }
        return Period.between(birthDate, today).getYears();
    }
}
