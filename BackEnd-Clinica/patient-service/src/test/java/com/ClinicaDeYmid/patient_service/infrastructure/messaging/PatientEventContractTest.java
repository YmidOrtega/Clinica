package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.support.PatientEventContract;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PatientEventContractTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-13T15:00:00Z");

    @Test
    void everyEventTypeMatchesThePublishedSchema() {
        Patient patient = PatientFixtures.registeredAdult();
        List<PatientEvent> allTypes = List.of(
                new PatientEvent.Registered(),
                new PatientEvent.DocumentChanged(new IdentityDocument(DocumentType.PASAPORTE, "AB123456")),
                new PatientEvent.DemographicsCorrected(),
                new PatientEvent.AffiliationUpdated(),
                new PatientEvent.Deactivated(),
                new PatientEvent.Reactivated());

        assertThat(allTypes).allSatisfy(event -> assertThat(violationsOf(event, patient)).isEmpty());
    }

    @Test
    void deathEventsCarryTheDeceasedSnapshot() {
        Patient patient = PatientFixtures.registeredAdult();
        patient.recordDeath(PatientFixtures.TODAY, PatientFixtures.today());

        assertThat(violationsOf(new PatientEvent.Died(PatientFixtures.TODAY), patient)).isEmpty();
        assertThat(json(new PatientEvent.Died(PatientFixtures.TODAY), patient))
                .contains("\"status\":\"DECEASED\"", "\"dateOfDeath\":\"2026-09-13\"");
    }

    @Test
    void uninsuredPatientsOmitTheHealthProvider() {
        Patient patient = PatientFixtures.registeredAdult();
        patient.updateAffiliation(Affiliation.uninsured());

        String json = json(new PatientEvent.AffiliationUpdated(), patient);

        assertThat(PatientEventContract.violations(json)).isEmpty();
        assertThat(json).doesNotContain("healthProviderNit");
    }

    @Test
    void neverSharesContactResidenceOrStatusReasons() {
        Patient patient = PatientFixtures.registeredAdult();
        patient.deactivate("Motivo interno con datos sensibles", PatientFixtures.today());

        String json = json(new PatientEvent.Deactivated(), patient);

        assertThat(Stream.of("mobile", "email", "phone", "address", "residence", "emergencyContact", "reason", "Motivo"))
                .allSatisfy(field -> assertThat(json).doesNotContain(field));
    }

    @Test
    void theSchemaRejectsEventsThatBreakTheContract() {
        String json = json(new PatientEvent.Registered(), PatientFixtures.registeredAdult());

        assertThat(PatientEventContract.violations(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))).isNotEmpty();
        assertThat(PatientEventContract.violations(json.replace("\"data\":{", "\"data\":{\"mobile\":\"3001234567\","))).isNotEmpty();
        assertThat(PatientEventContract.violations(json.replace("PatientRegistered", "PatientDocumentChanged"))).isNotEmpty();
    }

    private static List<String> violationsOf(PatientEvent event, Patient patient) {
        return PatientEventContract.violations(json(event, patient));
    }

    private static String json(PatientEvent event, Patient patient) {
        return PatientEventJson.write(PatientEventMessage.of(event, patient, UUID.randomUUID(), OCCURRED_AT,
                "4bf92f3577b34da6a3ce929d0e0e4736"));
    }
}
