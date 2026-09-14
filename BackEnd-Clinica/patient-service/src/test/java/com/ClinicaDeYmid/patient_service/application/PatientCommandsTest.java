package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.PatientStatus;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientCommandsTest {

    private final InMemoryPatients patients = new InMemoryPatients();
    private final StubHealthProviders healthProviders = new StubHealthProviders();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final PatientCommands commands = new PatientCommands(patients, healthProviders, outbox,
            TransactionOperations.withoutTransaction(), PatientFixtures.today());

    @Test
    void registersPatientsAfterValidatingTheirHealthProvider() {
        Patient patient = commands.register(PatientFixtures.adultRegistration());

        assertThat(patients.findByUuid(patient.uuid())).contains(patient);
        assertThat(healthProviders.requestedNits).containsExactly("900123456-7");
    }

    @Test
    void doesNotCallClientsServiceForUninsuredPatients() {
        commands.register(uninsuredRegistration());

        assertThat(healthProviders.requestedNits).isEmpty();
    }

    @Test
    void rejectsUnknownHealthProviders() {
        healthProviders.answer = new HealthProviderLookup.NotFound();

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()))
                .isInstanceOf(ApplicationException.HealthProviderNotFound.class);
        assertThat(patients.existsByDocument(PatientFixtures.cedula())).isFalse();
    }

    @Test
    void refusesToRegisterWhenTheHealthProviderCannotBeVerified() {
        healthProviders.answer = new HealthProviderLookup.Unavailable();

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()))
                .isInstanceOf(ApplicationException.HealthProviderUnavailable.class);
    }

    @Test
    void rejectsDuplicatedDocuments() {
        commands.register(PatientFixtures.adultRegistration());

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()))
                .isInstanceOf(PatientException.DocumentAlreadyRegistered.class);
    }

    @Test
    void rejectsChangesBasedOnAStaleVersion() {
        Patient patient = commands.register(PatientFixtures.adultRegistration());

        assertThatThrownBy(() -> commands.updateResidence(patient.uuid(), patient.version() + 1, PatientFixtures.residence()))
                .isInstanceOf(ApplicationException.StaleVersion.class);
    }

    @Test
    void reportsMissingPatients() {
        assertThatThrownBy(() -> commands.reactivate(UUID.randomUUID(), 0))
                .isInstanceOf(PatientException.NotFound.class);
    }

    @Test
    void preventsTakingAnotherPatientsDocument() {
        commands.register(PatientFixtures.adultRegistration());
        IdentityDocument otherDocument = new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "52123456");
        Patient other = commands.register(PatientFixtures.registration(otherDocument, PatientFixtures.adult(), null));

        assertThatThrownBy(() -> commands.changeDocument(other.uuid(), other.version(), PatientFixtures.cedula()))
                .isInstanceOf(PatientException.DocumentAlreadyRegistered.class);
        assertThat(commands.changeDocument(other.uuid(), other.version(), otherDocument).document()).isEqualTo(otherDocument);
    }

    @Test
    void verifiesTheNewHealthProviderWhenTheAffiliationChanges() {
        Patient patient = commands.register(uninsuredRegistration());
        healthProviders.answer = new HealthProviderLookup.NotFound();

        assertThatThrownBy(() -> commands.updateAffiliation(patient.uuid(), patient.version(), PatientFixtures.contributory()))
                .isInstanceOf(ApplicationException.HealthProviderNotFound.class);
        assertThat(patient.affiliation()).isEqualTo(Affiliation.uninsured());
    }

    @Test
    void appliesStatusTransitions() {
        Patient patient = commands.register(PatientFixtures.adultRegistration());

        commands.deactivate(patient.uuid(), patient.version(), "Registro duplicado");
        assertThat(patient.status()).isInstanceOf(PatientStatus.Inactive.class);

        commands.reactivate(patient.uuid(), patient.version());
        commands.recordDeath(patient.uuid(), patient.version(), PatientFixtures.TODAY);
        assertThat(patient.status()).isEqualTo(new PatientStatus.Deceased(PatientFixtures.TODAY));
    }

    @Test
    void appendsTheEventsOfEachChangeToTheOutboxAfterSaving() {
        Patient patient = commands.register(PatientFixtures.adultRegistration());
        commands.updateResidence(patient.uuid(), patient.version(), PatientFixtures.residence());
        commands.deactivate(patient.uuid(), patient.version(), "Registro duplicado");

        assertThat(outbox.appended).containsExactly(
                new PatientEvent.Registered(),
                new PatientEvent.Deactivated());
        assertThat(outbox.patients).containsOnly(patient);
    }

    @Test
    void appendsNothingWhenTheOperationFails() {
        healthProviders.answer = new HealthProviderLookup.NotFound();

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()));
        assertThat(outbox.appended).isEmpty();
    }

    private static PatientRegistration uninsuredRegistration() {
        return new PatientRegistration(PatientFixtures.cedula(), PatientFixtures.adult(), PatientFixtures.contact(), null,
                Affiliation.uninsured(), PatientFixtures.residence());
    }

    static final class RecordingOutbox implements PatientEventOutbox {

        final List<PatientEvent> appended = new ArrayList<>();
        final List<Patient> patients = new ArrayList<>();
        final List<UnidentifiedPatientEvent> appendedUnidentified = new ArrayList<>();

        @Override
        public void append(Patient patient, List<PatientEvent> events) {
            patients.add(patient);
            appended.addAll(events);
        }

        @Override
        public void appendUnidentified(UnidentifiedPatient patient, List<UnidentifiedPatientEvent> events) {
            appendedUnidentified.addAll(events);
        }
    }

    static final class StubHealthProviders implements HealthProviderDirectory {

        final List<String> requestedNits = new ArrayList<>();
        HealthProviderLookup answer = new HealthProviderLookup.Found(new HealthProvider("900123456-7", "Salud Total EPS", "EPS"));

        @Override
        public HealthProviderLookup findByNit(String nit) {
            requestedNits.add(nit);
            return answer;
        }
    }
}
