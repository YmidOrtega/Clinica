package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.domain.DocumentType;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientFixtures;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientStatus;
import com.ClinicaDeYmid.patient_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.patient_service.infrastructure.config.PersistenceConfiguration;
import com.ClinicaDeYmid.patient_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUnidentifiedPatients.class, JpaPatients.class, PersistenceConfiguration.class, ClockConfiguration.class,
        MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UnidentifiedPatientPersistenceIT {

    @Autowired
    private JpaUnidentifiedPatients unidentifiedPatients;

    @Autowired
    private JpaPatients patients;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM unidentified_patients_aud");
        jdbc.update("DELETE FROM unidentified_patients");
        jdbc.update("DELETE FROM unidentified_patient_codes");
    }

    @Test
    void generatesUniqueConsecutiveCodesUnderConcurrency() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Callable<String> nextCode = () -> transaction.execute(status -> unidentifiedPatients.nextCode(2026));
        List<String> codes;
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<String>> futures = executor.invokeAll(IntStream.range(0, 40).mapToObj(i -> nextCode).toList());
            codes = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }).toList();
        }

        assertThat(codes).doesNotHaveDuplicates().hasSize(40);
        assertThat(codes).contains("NN-2026-000001", "NN-2026-000040");
        String nextYear = transaction.execute(status -> unidentifiedPatients.nextCode(2027));
        assertThat(nextYear).isEqualTo("NN-2027-000001");
    }

    @Test
    void persistsTheIdentificationLinkAndItsHistory() {
        Patient patient = patients.save(Patient.register(PatientFixtures.registration(
                new IdentityDocument(DocumentType.CEDULA_DE_CIUDADANIA, "71234567"), PatientFixtures.adult(), null),
                PatientFixtures.today()));
        UnidentifiedPatient unidentified = unidentifiedPatients.save(UnidentifiedPatient.register(
                "NN-2026-000001", Sex.MALE, 1975, "Hombre con barba y camisa azul", PatientFixtures.today()));

        unidentified.identifyAs(patient, "Documento encontrado en su billetera", PatientFixtures.today());
        unidentifiedPatients.save(unidentified);

        UnidentifiedPatient reloaded = unidentifiedPatients.findByUuid(unidentified.uuid()).orElseThrow();
        assertThat(reloaded.status()).isInstanceOfSatisfying(UnidentifiedPatientStatus.Identified.class,
                identified -> assertThat(identified.patientUuid()).isEqualTo(patient.uuid()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unidentified_patients_aud WHERE id = (SELECT id FROM unidentified_patients WHERE uuid = ?)",
                Integer.class, unidentified.uuid().toString())).isEqualTo(2);
    }

    @Test
    void databaseRejectsLinksToPatientsThatDoNotExist() {
        assertThatThrownBy(() -> insert("IDENTIFIED", "'9b2f6c3e-1d4a-4b5c-8e7f-0a1b2c3d4e5f'", "'Cédula'", "NULL"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_unidentified_patients_identified_patient");
    }

    @Test
    void databaseRejectsInconsistentStatuses() {
        assertThatThrownBy(() -> insert("IDENTIFIED", "NULL", "'Cédula'", "NULL"))
                .hasMessageContaining("chk_unidentified_patients_status_consistency");
        assertThatThrownBy(() -> insert("DECEASED", "NULL", "NULL", "NULL"))
                .hasMessageContaining("chk_unidentified_patients_status_consistency");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO unidentified_patients (uuid, version, code, sex, estimated_birth_year, description, status, created_at, updated_at) "
                + "VALUES ('3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 0, 'NN-26-1', 'MALE', 1980, 'x', 'UNIDENTIFIED', NOW(6), NOW(6))"))
                .hasMessageContaining("chk_unidentified_patients_code");
    }

    private void insert(String status, String identifiedPatientUuid, String reason, String dateOfDeath) {
        jdbc.update("INSERT INTO unidentified_patients (uuid, version, code, sex, estimated_birth_year, description, status, "
                + "identified_patient_uuid, status_reason, date_of_death, created_at, updated_at) VALUES "
                + "('3f6c1b2a-7d4e-4a5b-9c8d-1e2f3a4b5c6d', 0, 'NN-2026-000099', 'MALE', 1980, 'Hombre', '" + status + "', "
                + identifiedPatientUuid + ", " + reason + ", " + dateOfDeath + ", NOW(6), NOW(6))");
    }
}
