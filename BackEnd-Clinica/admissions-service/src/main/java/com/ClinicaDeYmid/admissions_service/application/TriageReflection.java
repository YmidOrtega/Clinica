package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.Triage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TriageReflection {

    private static final Logger log = LoggerFactory.getLogger(TriageReflection.class);

    private final Admissions admissions;
    private final TransactionOperations transactions;

    public TriageReflection(Admissions admissions, TransactionOperations transactions) {
        this.admissions = admissions;
        this.transactions = transactions;
    }

    public void apply(UUID admissionUuid, Triage.Level level, Instant at, UUID clinician) {
        transactions.executeWithoutResult(status -> {
            Optional<Admission> episode = admissions.findByUuid(admissionUuid);
            if (episode.isEmpty()) {
                log.warn("Triage {} arrived for episode {}, which this service does not know", level, admissionUuid);
                return;
            }
            Admission admission = episode.get();
            if (admission.reflectTriage(level, at, clinician)) {
                admissions.save(admission);
                log.info("Episode {} reflects triage {}", admission.number(), level);
            } else {
                log.debug("Ignored an older triage for episode {}", admission.number());
            }
        });
    }
}
