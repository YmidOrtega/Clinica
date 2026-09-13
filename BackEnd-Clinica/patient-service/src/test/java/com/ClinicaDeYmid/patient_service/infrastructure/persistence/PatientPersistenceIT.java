package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.application.PatientHistory;
import com.ClinicaDeYmid.patient_service.domain.AffiliateType;
import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.ContactInfo;
import com.ClinicaDeYmid.patient_service.domain.Demographics;
import com.ClinicaDeYmid.patient_service.domain.Disability;
import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.HealthRegime;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.PatientStatus;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import com.ClinicaDeYmid.patient_service.domain.PersonName;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.patient_service.infrastructure.config.PersistenceConfiguration;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.patient_service.support.PatientRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPatients.class, EnversPatientHistory.class, PersistenceConfiguration.class, ClockConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PatientPersistenceIT {

    @Autowired
    private JpaPatients patients;

    @Autowired
    private EnversPatientHistory history;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM patients_aud");
        jdbc.update("DELETE FROM revisions");
        jdbc.update("DELETE FROM patients");
    }

    @Test
    void persistsEveryValueObjectOfThePatient() {
        Patient registered = patients.save(Patient.register(PatientFixtures.adultRegistration(), PatientFixtures.today()));

        Patient reloaded = patients.findByUuid(registered.uuid()).orElseThrow();

        assertThat(reloaded.document()).isEqualTo(PatientFixtures.cedula());
        assertThat(reloaded.demographics()).isEqualTo(PatientFixtures.adult());
        assertThat(reloaded.contact()).isEqualTo(PatientFixtures.contact());
        assertThat(reloaded.emergencyContact()).isNull();
        assertThat(reloaded.affiliation()).isEqualTo(PatientFixtures.contributory());
        assertThat(reloaded.residence()).isEqualTo(PatientFixtures.residence());
        assertThat(reloaded.status()).isEqualTo(new PatientStatus.Active());
        assertThat(reloaded.version()).isZero();
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void findsPatientsByDocument() {
        patients.save(Patient.register(PatientFixtures.adultRegistration(), PatientFixtures.today()));

        assertThat(patients.existsByDocument(PatientFixtures.cedula())).isTrue();
        assertThat(patients.findByDocument(PatientFixtures.cedula())).isPresent();
        assertThat(patients.existsByDocument(new IdentityDocument(DocumentType.PASAPORTE, "1098765432"))).isFalse();
    }

    @Test
    void recordsAnAuditRevisionForEveryChange() {
        Patient registered = patients.save(Patient.register(PatientFixtures.adultRegistration(), PatientFixtures.today()));
        inTransaction(() -> {
            Patient patient = patients.findByUuid(registered.uuid()).orElseThrow();
            patient.updateContact(new ContactInfo("3109998877", null, null), null, PatientFixtures.today());
            patients.save(patient);
        });

        List<PatientHistory.Revision> revisions = history.of(registered.uuid());

        assertThat(revisions).extracting(PatientHistory.Revision::changeType)
                .containsExactly(PatientHistory.ChangeType.CREATED, PatientHistory.ChangeType.UPDATED);
        assertThat(revisions.get(0).state().contact().mobile()).isEqualTo("3001234567");
        assertThat(revisions.get(1).state().contact().mobile()).isEqualTo("3109998877");
        assertThat(revisions.get(1).state().affiliation()).isEqualTo(PatientFixtures.contributory());
        assertThat(revisions).allSatisfy(revision -> assertThat(revision.revisedAt()).isNotNull());
    }

    @Test
    void rejectsConcurrentModificationsOfTheSameVersion() {
        Patient registered = patients.save(Patient.register(PatientFixtures.adultRegistration(), PatientFixtures.today()));
        Patient firstCopy = patients.findByUuid(registered.uuid()).orElseThrow();
        Patient secondCopy = patients.findByUuid(registered.uuid()).orElseThrow();

        firstCopy.updateResidence(PatientFixtures.residence());
        firstCopy.updateAffiliation(Affiliation.uninsured());
        patients.save(firstCopy);
        secondCopy.updateAffiliation(new Affiliation(HealthRegime.SUBSIDIZED, AffiliateType.BENEFICIARY, "800111222-3", null));

        assertThatThrownBy(() -> patients.save(secondCopy)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void searchesByAccentAndCaseInsensitiveNamePrefixesInAlphabeticalOrder() {
        save("1000000001", "Ana María", "Gómez Restrepo");
        save("1000000002", "Andrés", "Gomez Pérez");
        save("1000000003", "Lucía", "Restrepo Ortiz");
        save("1000000004", "Juan", "De la Cruz");

        assertThat(namesMatching("GOMEZ", null)).containsExactly("Andrés Gomez Pérez", "Ana María Gómez Restrepo");
        assertThat(namesMatching("gómez", "ana")).containsExactly("Ana María Gómez Restrepo");
        assertThat(namesMatching("restrepo", "LUCIA")).containsExactly("Lucía Restrepo Ortiz");
        assertThat(namesMatching("de la", null)).containsExactly("Juan De la Cruz");
        assertThat(namesMatching("restrepo", "ana")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "chk_patients_document_number          | document_number        | 'ab-12'",
            "chk_patients_numeric_document_number  | document_number        | 'AB1234'",
            "chk_patients_birth_date               | birth_date             | '1899-12-31'",
            "chk_patients_sex                      | sex                    | 'UNKNOWN'",
            "chk_patients_mobile                   | mobile                 | '300-123'",
            "chk_patients_email                    | email                  | 'not-an-email'",
            "chk_patients_emergency_contact_complete | emergency_contact_name | 'Luis Restrepo'",
            "chk_patients_affiliation_consistency  | health_regime          | 'UNINSURED'",
            "chk_patients_status_consistency       | status                 | 'DECEASED'",
            "chk_patients_first_names              | first_names            | '   '"
    })
    void databaseRejectsInvalidRowsWrittenOutsideTheApplication(String constraint, String column, String value) {
        Map<String, String> row = PatientRows.valid();
        row.put(column, value);

        assertThatThrownBy(() -> PatientRows.insert(jdbc, row))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void databaseAcceptsTheReferenceRow() {
        PatientRows.insert(jdbc, PatientRows.valid());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicatedDocuments() {
        PatientRows.insert(jdbc, PatientRows.valid());
        Map<String, String> duplicate = PatientRows.valid();
        duplicate.put("uuid", "'9b2f6c3e-1d4a-4b5c-8e7f-0a1b2c3d4e5f'");

        assertThatThrownBy(() -> PatientRows.insert(jdbc, duplicate)).hasMessageContaining("uk_patients_document");
    }

    private void save(String documentNumber, String firstNames, String lastNames) {
        Demographics demographics = new Demographics(new PersonName(firstNames, lastNames), LocalDate.of(1990, 3, 1),
                Sex.FEMALE, "CO", Disability.NONE);
        patients.save(Patient.register(new PatientRegistration(
                new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, documentNumber), demographics,
                PatientFixtures.contact(), null, PatientFixtures.contributory(), PatientFixtures.residence()), PatientFixtures.today()));
    }

    private List<String> namesMatching(String lastNames, String firstNames) {
        Page<Patient> page = patients.searchByName(new Patients.NameQuery(lastNames, firstNames), PageRequest.of(0, 20));
        return page.map(patient -> patient.demographics().name().fullName()).getContent();
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
