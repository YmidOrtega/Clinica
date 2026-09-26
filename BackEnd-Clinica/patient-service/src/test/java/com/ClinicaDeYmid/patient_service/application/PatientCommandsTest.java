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

import com.ClinicaDeYmid.patient_service.domain.Residence;

import com.ClinicaDeYmid.patient_service.domain.Zone;

import com.ClinicaDeYmid.patient_service.domain.Demographics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientCommandsTest {

    private final InMemoryPatients patients = new InMemoryPatients();
    private final StubPayers payers = new StubPayers();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final PatientCommands commands = new PatientCommands(patients, payers, new StubGeography(), outbox,
            TransactionOperations.withoutTransaction(), PatientFixtures.today());

    @Test
    void codesTheCountryOfOriginAndValidatesTheResidenceMunicipality() {
        PatientRegistration base = PatientFixtures.adultRegistration();
        Residence coded = new Residence("Santander", "Bucaramanga", "68001", Zone.URBAN, "Calle 45 # 27-10");

        Patient patient = commands.register(new PatientRegistration(base.document(), base.demographics(), base.contact(),
                base.emergencyContact(), base.affiliation(), coded));

        assertThat(patient.demographics().countryOfOriginCode()).isEqualTo("170");
        assertThat(patient.residence().municipalityCode()).isEqualTo("68001");
        assertThatThrownBy(() -> commands.updateResidence(patient.uuid(), patient.version(),
                new Residence("Santander", "Bucaramanga", "99999", Zone.URBAN, "Calle 45 # 27-10")))
                .isInstanceOf(ApplicationException.UnknownPlace.class);
        assertThatThrownBy(() -> new Residence("Santander", "Bucaramanga", "6800", Zone.URBAN, "Calle 1"))
                .isInstanceOf(PatientException.InvalidData.class);
    }

    @Test
    void refusesACountryOfOriginOutsideTheReferenceTable() {
        PatientRegistration base = PatientFixtures.adultRegistration();
        Demographics foreign = new Demographics(base.demographics().name(), base.demographics().birthDate(),
                base.demographics().sex(), "ZZ", base.demographics().disability());

        assertThatThrownBy(() -> commands.register(new PatientRegistration(base.document(), foreign, base.contact(),
                base.emergencyContact(), base.affiliation(), base.residence())))
                .isInstanceOf(ApplicationException.UnknownPlace.class);
    }

    @Test
    void registersPatientsAfterValidatingTheirPayer() {
        Patient patient = commands.register(PatientFixtures.adultRegistration());

        assertThat(patients.findByUuid(patient.uuid())).contains(patient);
        assertThat(payers.requestedPayers).containsExactly(PatientFixtures.PAYER_UUID);
    }

    @Test
    void doesNotCallClientsServiceForUninsuredPatients() {
        commands.register(uninsuredRegistration());

        assertThat(payers.requestedPayers).isEmpty();
    }

    @Test
    void rejectsUnknownPayers() {
        payers.answer = new PayerLookup.NotFound();

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()))
                .isInstanceOf(ApplicationException.PayerNotFound.class);
        assertThat(patients.existsByDocument(PatientFixtures.cedula())).isFalse();
    }

    @Test
    void refusesToRegisterWhenThePayerCannotBeVerified() {
        payers.answer = new PayerLookup.Unavailable();

        assertThatThrownBy(() -> commands.register(PatientFixtures.adultRegistration()))
                .isInstanceOf(ApplicationException.PayerUnavailable.class);
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
    void verifiesTheNewPayerWhenTheAffiliationChanges() {
        Patient patient = commands.register(uninsuredRegistration());
        payers.answer = new PayerLookup.NotFound();

        assertThatThrownBy(() -> commands.updateAffiliation(patient.uuid(), patient.version(), PatientFixtures.contributory()))
                .isInstanceOf(ApplicationException.PayerNotFound.class);
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
        payers.answer = new PayerLookup.NotFound();

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

    static final class StubPayers implements PayerDirectory {

        final List<UUID> requestedPayers = new ArrayList<>();
        PayerLookup answer = new PayerLookup.Found(
                new Payer(PatientFixtures.PAYER_UUID, "901234567-7", "Salud Total EPS", "EPS"));

        @Override
        public PayerLookup findByUuid(UUID uuid) {
            requestedPayers.add(uuid);
            return answer;
        }
    }
}
