package com.ClinicaDeYmid.admissions_service.application.coverage;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Coverage;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class CoverageGate {

    private static final Logger log = LoggerFactory.getLogger(CoverageGate.class);

    private final CoverageChecker checker;
    private final Clock clock;

    public CoverageGate(CoverageChecker checker, Clock clock) {
        this.checker = checker;
        this.clock = clock;
    }

    public Coverage assess(PatientReference patient, AdmissionKind kind, boolean overridden) {
        Instant now = Instant.now(clock);
        CoverageVerdict verdict = checker.check(patient, LocalDate.now(clock));
        return switch (verdict) {
            case CoverageVerdict.Covered covered ->
                    Coverage.covered(covered.contractUuid(), covered.contractNumber(), covered.payerUuid(), now);
            case CoverageVerdict.NotCovered notCovered -> refuseOrMark(notCovered, kind, overridden, now);
            case CoverageVerdict.Unknown unknown -> {
                log.warn("Coverage could not be verified for patient {} ({}); admitting with a pending mark",
                        patient.uuid(), unknown.reason());
                yield Coverage.unknown(unknown.reason(), unknown.payerUuid(), now);
            }
        };
    }

    private Coverage refuseOrMark(CoverageVerdict.NotCovered notCovered, AdmissionKind kind, boolean overridden,
                                  Instant now) {
        if (kind.coverageMayBlockAdmission() && !overridden) {
            throw new AdmissionsException.WithoutCoverage(notCovered.reason());
        }
        if (overridden) {
            log.warn("Coverage was overridden by an administrator: {}", notCovered.reason());
        } else {
            log.info("Emergency admission without coverage is allowed by law: {}", notCovered.reason());
        }
        return Coverage.notCovered(notCovered.reason(), notCovered.payerUuid(), now);
    }
}
