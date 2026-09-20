package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class ProfessionalRegistration {

    private static final Pattern NUMBER = Pattern.compile("^[A-Z0-9][A-Z0-9-]{4,29}$");

    @Column(name = "registration_number", nullable = false, length = 30)
    private String number;

    @Column(name = "registered_on")
    private LocalDate registeredOn;

    protected ProfessionalRegistration() {
    }

    public ProfessionalRegistration(String number, LocalDate registeredOn, Clock clock) {
        this.number = Rules.matching(Rules.upper(Rules.requiredText(number, "registration.number", 30)),
                NUMBER, "registration.number");
        if (registeredOn != null && registeredOn.isAfter(LocalDate.now(clock))) {
            throw new PractitionersException.InvalidData("registration.registeredOn", "no puede estar en el futuro");
        }
        this.registeredOn = registeredOn;
    }

    public String number() {
        return number;
    }

    public LocalDate registeredOn() {
        return registeredOn;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProfessionalRegistration registration
                && Objects.equals(number, registration.number) && Objects.equals(registeredOn, registration.registeredOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, registeredOn);
    }
}
