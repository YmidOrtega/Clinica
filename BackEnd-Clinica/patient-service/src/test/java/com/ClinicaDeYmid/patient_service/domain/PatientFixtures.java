package com.ClinicaDeYmid.patient_service.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class PatientFixtures {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    public static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    private PatientFixtures() {
    }

    public static Clock clockAt(LocalDate date) {
        return Clock.fixed(ZonedDateTime.of(date.atTime(10, 0), BOGOTA).toInstant(), BOGOTA);
    }

    public static Clock today() {
        return clockAt(TODAY);
    }

    public static IdentityDocument cedula() {
        return new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "1098765432");
    }

    public static Demographics adultBornOn(LocalDate birthDate) {
        return new Demographics(new PersonName("Ana María", "Restrepo Gómez"), birthDate, Sex.FEMALE, "CO", Disability.NONE);
    }

    public static Demographics adult() {
        return adultBornOn(TODAY.minusYears(35));
    }

    public static ContactInfo contact() {
        return new ContactInfo("3001234567", null, "ana@example.com");
    }

    public static EmergencyContact emergencyContact() {
        return new EmergencyContact("Luis Restrepo", Relationship.FATHER, "3017654321");
    }

    public static Affiliation contributory() {
        return new Affiliation(HealthRegime.CONTRIBUTORY, AffiliateType.HOLDER, "900123456-7", null);
    }

    public static Residence residence() {
        return new Residence("Santander", "Bucaramanga", Zone.URBAN, "Calle 45 # 27-10");
    }

    public static PatientRegistration adultRegistration() {
        return new PatientRegistration(cedula(), adult(), contact(), null, contributory(), residence());
    }

    public static PatientRegistration registration(IdentityDocument document, Demographics demographics, EmergencyContact emergencyContact) {
        return new PatientRegistration(document, demographics, contact(), emergencyContact, contributory(), residence());
    }

    public static Patient registeredAdult() {
        return Patient.register(adultRegistration(), today());
    }
}
