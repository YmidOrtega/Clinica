package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionSearch;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.DeathNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class JpaAdmissions implements Admissions {

    private final AdmissionJpaRepository repository;
    private final AdmissionSearchQuery searches;
    private final JdbcTemplate jdbc;

    JpaAdmissions(AdmissionJpaRepository repository, AdmissionSearchQuery searches, JdbcTemplate jdbc) {
        this.repository = repository;
        this.searches = searches;
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
    public Optional<Admission> findOpenByPatient(UUID patientUuid) {
        return repository.findOpenByPatient(patientUuid).stream().findFirst();
    }

    @Override
    public Page<Admission> search(AdmissionSearch criteria, Pageable pageable) {
        List<UUID> matches = searches.matches(criteria, pageable);
        if (matches.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, searches.count(criteria));
        }
        return new PageImpl<>(inOrder(matches), pageable, searches.count(criteria));
    }

    @Override
    public Page<Admission> findWithPendingCoverage(Pageable pageable) {
        return hydrated(repository.findByCoverageOtherThan(Coverage.Code.COVERED, pageable));
    }

    @Override
    public Page<Admission> findWithPendingDeathNotice(Pageable pageable) {
        return hydrated(repository.findByDeathNoticeStatus(DeathNotice.Status.PENDING, pageable));
    }

    @Override
    public List<Admission> findQueueOf(UUID configurationServiceUuid) {
        return repository.findQueueOf(configurationServiceUuid).stream()
                .sorted(Comparator.comparing(admission -> admission.currentPhase().startedAt()))
                .toList();
    }

    @Override
    public List<Admission> findByBeds(Collection<UUID> bedUuids) {
        return bedUuids.isEmpty() ? List.of() : repository.findByBedUuidIn(bedUuids);
    }

    private Page<Admission> hydrated(Page<Admission> page) {
        List<UUID> uuids = page.getContent().stream().map(Admission::uuid).toList();
        if (uuids.isEmpty()) {
            return page;
        }
        return new PageImpl<>(inOrder(uuids), page.getPageable(), page.getTotalElements());
    }

    private List<Admission> inOrder(List<UUID> uuids) {
        Map<UUID, Admission> loaded = repository.findAllByUuidIn(uuids).stream()
                .collect(Collectors.toMap(Admission::uuid, admission -> admission));
        return uuids.stream().map(loaded::get).toList();
    }

    @Override
    public String nextNumber(int year) {
        Long sequence = jdbc.queryForObject("SELECT nextval('admissions.admission_number_seq')", Long.class);
        return "ADM-%d-%06d".formatted(year, sequence);
    }
}
