package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaAdmissions implements Admissions {

    private final AdmissionJpaRepository repository;
    private final JdbcTemplate jdbc;

    JpaAdmissions(AdmissionJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public Admission save(Admission admission) {
        return repository.saveAndFlush(admission);
    }

    @Override
    public Optional<Admission> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Admission> findByNumber(String number) {
        return repository.findByNumber(number);
    }

    @Override
    public Optional<Admission> findOpenByPatient(UUID patientUuid) {
        return repository.findOpenByPatient(patientUuid).stream().findFirst();
    }

    @Override
    public List<Admission> findByPatient(UUID patientUuid) {
        return repository.findByPatient(patientUuid);
    }

    @Override
    public List<Admission> findWithPendingCoverage() {
        return repository.findByCoverageOtherThan(Coverage.Code.COVERED);
    }

    @Override
    public String nextNumber(int year) {
        Long sequence = jdbc.queryForObject("SELECT nextval('admissions.admission_number_seq')", Long.class);
        return "ADM-%d-%06d".formatted(year, sequence);
    }
}
