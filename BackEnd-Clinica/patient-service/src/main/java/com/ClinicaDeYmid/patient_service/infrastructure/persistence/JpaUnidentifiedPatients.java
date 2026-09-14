package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientCode;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatients;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUnidentifiedPatients implements UnidentifiedPatients {

    private static final String INCREMENT_SEQUENCE = """
            INSERT INTO unidentified_patient_codes (code_year, sequence_value) VALUES (?, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE sequence_value = LAST_INSERT_ID(sequence_value + 1)
            """;

    private final UnidentifiedPatientJpaRepository repository;
    private final JdbcTemplate jdbc;

    JpaUnidentifiedPatients(UnidentifiedPatientJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public UnidentifiedPatient save(UnidentifiedPatient patient) {
        return repository.saveAndFlush(patient);
    }

    @Override
    public Optional<UnidentifiedPatient> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCode(int year) {
        jdbc.update(INCREMENT_SEQUENCE, year);
        Long sequence = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return UnidentifiedPatientCode.of(year, sequence);
    }
}
