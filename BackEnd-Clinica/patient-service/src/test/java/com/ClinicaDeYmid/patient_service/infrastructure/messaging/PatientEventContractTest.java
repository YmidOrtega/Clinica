package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
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

    @Test
    void unidentifiedPatientEventsMatchTheSchemaWithoutTheDescription() {
        UnidentifiedPatient unidentified = UnidentifiedPatient.register("NN-2026-000007", Sex.INDETERMINATE, 1970,
                "Persona adulta con chaqueta roja encontrada en la vía", PatientFixtures.today());
        String registered = json(new UnidentifiedPatientEvent.Registered(), unidentified);

        Patient patient = PatientFixtures.registeredAdult();
        unidentified.identifyAs(patient, "Cédula", PatientFixtures.today());
        String identified = json(new UnidentifiedPatientEvent.Identified(patient.uuid()), unidentified);

        unidentified.revertIdentification("No era la persona", PatientFixtures.today());
        String reverted = json(new UnidentifiedPatientEvent.IdentificationReverted(patient.uuid()), unidentified);

        unidentified.recordDeath(PatientFixtures.TODAY, PatientFixtures.today());
        String died = json(new UnidentifiedPatientEvent.Died(PatientFixtures.TODAY), unidentified);

        assertThat(List.of(registered, identified, reverted, died))
                .allSatisfy(json -> assertThat(PatientEventContract.violations(json)).isEmpty())
                .allSatisfy(json -> assertThat(json).doesNotContain("chaqueta", "description", "Cédula", "No era la persona"));
        assertThat(identified).contains("\"identifiedPatientUuid\":\"" + patient.uuid() + "\"");
        assertThat(reverted).contains("\"previousPatientUuid\":\"" + patient.uuid() + "\"");
    }

    @Test
    void theSchemaKeepsPatientAndUnidentifiedEventsApart() {
        UnidentifiedPatient unidentified = UnidentifiedPatient.register("NN-2026-000008", Sex.MALE, 1990, "Hombre", PatientFixtures.today());
        String json = json(new UnidentifiedPatientEvent.Registered(), unidentified);

        assertThat(PatientEventContract.violations(json.replace("UnidentifiedPatientRegistered", "PatientRegistered"))).isNotEmpty();
        assertThat(PatientEventContract.violations(json.replace("UnidentifiedPatientRegistered", "UnidentifiedPatientIdentified"))).isNotEmpty();
    }

    private static List<String> violationsOf(PatientEvent event, Patient patient) {
        return PatientEventContract.violations(json(event, patient));
    }

    private static String json(UnidentifiedPatientEvent event, UnidentifiedPatient patient) {
        return PatientEventJson.write(UnidentifiedPatientEventMessage.of(event, patient, UUID.randomUUID(), OCCURRED_AT,
                "4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    private static String json(PatientEvent event, Patient patient) {
        return PatientEventJson.write(PatientEventMessage.of(event, patient, UUID.randomUUID(), OCCURRED_AT,
                "4bf92f3577b34da6a3ce929d0e0e4736"));
    }
}
