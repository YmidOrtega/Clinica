package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;

import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.TODAY;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.adult;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.adultBornOn;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.cedula;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.clockAt;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.contact;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.emergencyContact;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.registeredAdult;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.registration;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientTest {

    @Test
    void registersActivePatientsWithAPublicIdentifier() {
        Patient patient = registeredAdult();

        assertThat(patient.uuid()).isNotNull();
        assertThat(patient.status()).isEqualTo(new PatientStatus.Active());
        assertThat(patient.document()).isEqualTo(cedula());
        assertThat(patient.demographics()).isEqualTo(adult());
    }

    @Test
    void rejectsDocumentsThatDoNotMatchTheAge() {
        Demographics child = adultBornOn(TODAY.minusYears(10));

        assertThatThrownBy(() -> Patient.register(registration(cedula(), child, emergencyContact()), today()))
                .isInstanceOf(PatientException.DocumentNotValidForAge.class);
    }

    @Test
    void requiresEmergencyContactForMinors() {
        IdentityDocument tarjeta = new IdentityDocument(DocumentType.TARJETA_DE_IDENTIDAD, "1005123456");
        Demographics teenager = adultBornOn(TODAY.minusYears(15));

        assertThatThrownBy(() -> Patient.register(registration(tarjeta, teenager, null), today()))
                .isInstanceOf(PatientException.EmergencyContactRequired.class);
        assertThatCode(() -> Patient.register(registration(tarjeta, teenager, emergencyContact()), today()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFutureBirthDates() {
        assertThatThrownBy(() -> Patient.register(registration(cedula(), adultBornOn(TODAY.plusDays(1)), null), today()))
                .isInstanceOf(PatientException.InvalidData.class);
    }

    @Test
    void keepsAcceptingUpdatesAfterTheDocumentAgeRangeIsOutgrown() {
        IdentityDocument tarjeta = new IdentityDocument(DocumentType.TARJETA_DE_IDENTIDAD, "1005123456");
        Patient patient = Patient.register(registration(tarjeta, adultBornOn(TODAY.minusYears(17)), emergencyContact()), today());
        Clock oneYearLater = clockAt(TODAY.plusYears(1));

        assertThatCode(() -> patient.updateContact(contact(), null, oneYearLater)).doesNotThrowAnyException();
        assertThatCode(() -> patient.updateResidence(PatientFixtures.residence())).doesNotThrowAnyException();
    }

    @Test
    void validatesTheAgeWhenTheDocumentChanges() {
        IdentityDocument tarjeta = new IdentityDocument(DocumentType.TARJETA_DE_IDENTIDAD, "1005123456");
        Patient patient = Patient.register(registration(tarjeta, adultBornOn(TODAY.minusYears(17)), emergencyContact()), today());

        assertThatThrownBy(() -> patient.changeDocument(cedula(), today()))
                .isInstanceOf(PatientException.DocumentNotValidForAge.class);

        patient.changeDocument(cedula(), clockAt(TODAY.plusYears(1)));
        assertThat(patient.document()).isEqualTo(cedula());
    }

    @Test
    void revalidatesRulesWhenDemographicsAreCorrected() {
        Patient patient = registeredAdult();

        assertThatThrownBy(() -> patient.correctDemographics(adultBornOn(TODAY.minusYears(12)), today()))
                .isInstanceOf(PatientException.DocumentNotValidForAge.class);

        patient.correctDemographics(adultBornOn(LocalDate.of(1980, 5, 20)), today());
        assertThat(patient.demographics().birthDate()).isEqualTo(LocalDate.of(1980, 5, 20));
    }

    @Test
    void doesNotAllowRemovingTheEmergencyContactOfAMinor() {
        IdentityDocument tarjeta = new IdentityDocument(DocumentType.TARJETA_DE_IDENTIDAD, "1005123456");
        Patient patient = Patient.register(registration(tarjeta, adultBornOn(TODAY.minusYears(15)), emergencyContact()), today());

        assertThatThrownBy(() -> patient.updateContact(contact(), null, today()))
                .isInstanceOf(PatientException.EmergencyContactRequired.class);
    }

    @Test
    void blocksChangesWhileInactiveAndAllowsThemAfterReactivation() {
        Patient patient = registeredAdult();

        patient.deactivate("Registro duplicado", today());

        assertThat(patient.status()).isInstanceOf(PatientStatus.Inactive.class);
        assertThatThrownBy(() -> patient.updateAffiliation(Affiliation.uninsured()))
                .isInstanceOf(PatientException.NotActive.class);

        patient.reactivate(today());

        assertThat(patient.status()).isEqualTo(new PatientStatus.Active());
        assertThatCode(() -> patient.updateAffiliation(Affiliation.uninsured())).doesNotThrowAnyException();
    }

    @Test
    void recordsDeathWithinValidDates() {
        Patient patient = registeredAdult();

        assertThatThrownBy(() -> patient.recordDeath(TODAY.plusDays(1), today()))
                .isInstanceOf(PatientException.InvalidData.class);
        assertThatThrownBy(() -> patient.recordDeath(adult().birthDate().minusDays(1), today()))
                .isInstanceOf(PatientException.InvalidData.class);

        patient.recordDeath(TODAY, today());

        assertThat(patient.status()).isEqualTo(new PatientStatus.Deceased(TODAY));
    }

    @Test
    void deceasedPatientsCannotBeModifiedOrReactivated() {
        Patient patient = registeredAdult();
        patient.recordDeath(TODAY, today());

        assertThatThrownBy(() -> patient.updateContact(contact(), null, today())).isInstanceOf(PatientException.NotActive.class);
        assertThatThrownBy(() -> patient.reactivate(today())).isInstanceOf(PatientException.InvalidStatusTransition.class);
    }

    @Test
    void identifiesPatientsByTheirPublicIdentifier() {
        Patient first = registeredAdult();
        Patient second = registeredAdult();

        assertThat(first).isEqualTo(first).isNotEqualTo(second);
    }
}
