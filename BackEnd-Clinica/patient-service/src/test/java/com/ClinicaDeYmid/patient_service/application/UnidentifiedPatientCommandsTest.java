package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnidentifiedPatientCommandsTest {

    private final InMemoryPatients patients = new InMemoryPatients();
    private final InMemoryUnidentifiedPatients unidentifiedPatients = new InMemoryUnidentifiedPatients();
    private final PatientCommandsTest.StubHealthProviders healthProviders = new PatientCommandsTest.StubHealthProviders();
    private final PatientCommandsTest.RecordingOutbox outbox = new PatientCommandsTest.RecordingOutbox();
    private final PatientCommands patientCommands = new PatientCommands(patients, healthProviders, outbox,
            TransactionOperations.withoutTransaction(), PatientFixtures.today());
    private final UnidentifiedPatientCommands commands = new UnidentifiedPatientCommands(unidentifiedPatients, patients,
            patientCommands, outbox, TransactionOperations.withoutTransaction(), PatientFixtures.today());

    @Test
    void registersWithConsecutiveCodesForTheCurrentYear() {
        UnidentifiedPatient first = register();
        UnidentifiedPatient second = register();

        assertThat(first.code()).isEqualTo("NN-2026-000001");
        assertThat(second.code()).isEqualTo("NN-2026-000002");
        assertThat(outbox.appendedUnidentified).containsOnly(new UnidentifiedPatientEvent.Registered());
    }

    @Test
    void identifiesAsAnExistingPatient() {
        Patient patient = patientCommands.register(PatientFixtures.adultRegistration());
        UnidentifiedPatient unidentified = register();

        UnidentifiedPatient identified = commands.identifyAsExisting(unidentified.uuid(), unidentified.version(), patient.uuid(),
                "Familiar presentó la cédula");

        assertThat(identified.status()).isInstanceOf(UnidentifiedPatientStatus.Identified.class);
        assertThat(outbox.appendedUnidentified).endsWith(new UnidentifiedPatientEvent.Identified(patient.uuid()));
    }

    @Test
    void rejectsIdentificationWithAnUnknownPatient() {
        UnidentifiedPatient unidentified = register();

        assertThatThrownBy(() -> commands.identifyAsExisting(unidentified.uuid(), unidentified.version(), UUID.randomUUID(), "Cédula"))
                .isInstanceOf(PatientException.NotFound.class);
        assertThat(unidentified.status()).isEqualTo(new UnidentifiedPatientStatus.Unidentified());
    }

    @Test
    void registersTheRealPatientAndLinksItInOneStep() {
        UnidentifiedPatient unidentified = register();

        UnidentifiedPatient identified = commands.identifyAsNew(unidentified.uuid(), unidentified.version(),
                PatientFixtures.adultRegistration(), "Recuperó la conciencia y dio sus datos");

        Patient registered = patients.findByDocument(PatientFixtures.cedula()).orElseThrow();
        assertThat(identified.status()).isInstanceOfSatisfying(UnidentifiedPatientStatus.Identified.class,
                status -> assertThat(status.patientUuid()).isEqualTo(registered.uuid()));
        assertThat(outbox.appended).containsExactly(new PatientEvent.Registered());
        assertThat(healthProviders.requestedNits).containsExactly("900123456-7");
    }

    @Test
    void doesNotRegisterTheRealPatientWhenTheVersionIsStale() {
        UnidentifiedPatient unidentified = register();

        assertThatThrownBy(() -> commands.identifyAsNew(unidentified.uuid(), unidentified.version() + 1,
                PatientFixtures.adultRegistration(), "Datos"))
                .isInstanceOf(ApplicationException.StaleVersion.class);
        assertThat(patients.existsByDocument(PatientFixtures.cedula())).isFalse();
    }

    @Test
    void refusesToIdentifyAsNewWhenTheHealthProviderDoesNotExist() {
        UnidentifiedPatient unidentified = register();
        healthProviders.answer = new HealthProviderLookup.NotFound();

        assertThatThrownBy(() -> commands.identifyAsNew(unidentified.uuid(), unidentified.version(),
                PatientFixtures.adultRegistration(), "Datos"))
                .isInstanceOf(ApplicationException.HealthProviderNotFound.class);
        assertThat(patients.existsByDocument(PatientFixtures.cedula())).isFalse();
    }

    @Test
    void revertsIdentificationsAndRecordsDeaths() {
        Patient patient = patientCommands.register(PatientFixtures.adultRegistration());
        UnidentifiedPatient unidentified = register();
        commands.identifyAsExisting(unidentified.uuid(), unidentified.version(), patient.uuid(), "Parecido");

        commands.revertIdentification(unidentified.uuid(), unidentified.version(), "No era la persona");
        commands.recordDeath(unidentified.uuid(), unidentified.version(), PatientFixtures.TODAY);

        assertThat(unidentified.status()).isEqualTo(new UnidentifiedPatientStatus.Deceased(PatientFixtures.TODAY));
        assertThat(outbox.appendedUnidentified).containsSubsequence(
                new UnidentifiedPatientEvent.IdentificationReverted(patient.uuid()),
                new UnidentifiedPatientEvent.Died(PatientFixtures.TODAY));
    }

    @Test
    void reportsMissingUnidentifiedPatients() {
        assertThatThrownBy(() -> commands.recordDeath(UUID.randomUUID(), 0, PatientFixtures.TODAY))
                .isInstanceOf(PatientException.UnidentifiedNotFound.class);
    }

    private UnidentifiedPatient register() {
        return commands.register(Sex.FEMALE, 1985, "Mujer adulta, tatuaje de golondrina en la muñeca derecha");
    }
}
