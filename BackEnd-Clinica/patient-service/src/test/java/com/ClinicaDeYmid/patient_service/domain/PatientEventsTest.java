package com.ClinicaDeYmid.patient_service.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.TODAY;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.adultBornOn;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.cedula;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.contact;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.registeredAdult;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.residence;
import static com.ClinicaDeYmid.patient_service.domain.PatientFixtures.today;
import static org.assertj.core.api.Assertions.assertThat;

class PatientEventsTest {

    @Test
    void recordsTheRegistration() {
        assertThat(registeredAdult().pullEvents()).containsExactly(new PatientEvent.Registered());
    }

    @Test
    void handsOverEachEventOnlyOnce() {
        Patient patient = registeredAdult();
        patient.pullEvents();

        assertThat(patient.pullEvents()).isEmpty();
    }

    @Test
    void keepsThePreviousDocumentWhenItChanges() {
        Patient patient = pulled(registeredAdult());
        IdentityDocument passport = new IdentityDocument(DocumentType.PASAPORTE, "AB123456");

        patient.changeDocument(passport, today());

        assertThat(patient.pullEvents()).containsExactly(new PatientEvent.DocumentChanged(cedula()));
    }

    @Test
    void ignoresChangesThatLeaveTheSharedDataUntouched() {
        Patient patient = pulled(registeredAdult());

        patient.changeDocument(cedula(), today());
        patient.correctDemographics(patient.demographics(), today());
        patient.updateAffiliation(patient.affiliation());

        assertThat(patient.pullEvents()).isEmpty();
    }

    @Test
    void doesNotPublishContactOrResidenceChanges() {
        Patient patient = pulled(registeredAdult());

        patient.updateContact(contact(), null, today());
        patient.updateResidence(residence());

        assertThat(patient.pullEvents()).isEmpty();
    }

    @Test
    void recordsCorrectionsAffiliationChangesAndStatusTransitionsInOrder() {
        Patient patient = pulled(registeredAdult());

        patient.correctDemographics(adultBornOn(LocalDate.of(1980, 5, 20)), today());
        patient.updateAffiliation(Affiliation.uninsured());
        patient.deactivate("Registro duplicado", today());
        patient.reactivate(today());
        patient.recordDeath(TODAY, today());

        assertThat(patient.pullEvents()).containsExactly(
                new PatientEvent.DemographicsCorrected(),
                new PatientEvent.AffiliationUpdated(),
                new PatientEvent.Deactivated(),
                new PatientEvent.Reactivated(),
                new PatientEvent.Died(TODAY));
    }

    private static Patient pulled(Patient patient) {
        patient.pullEvents();
        return patient;
    }
}
