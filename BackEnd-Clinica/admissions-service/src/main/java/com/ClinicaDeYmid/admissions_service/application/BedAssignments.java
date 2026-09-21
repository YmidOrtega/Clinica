package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionEvent;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Bed;
import com.ClinicaDeYmid.admissions_service.domain.BedStay;
import com.ClinicaDeYmid.admissions_service.domain.BedStays;
import com.ClinicaDeYmid.admissions_service.domain.Beds;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BedAssignments {

    private static final Logger log = LoggerFactory.getLogger(BedAssignments.class);

    private final Admissions admissions;
    private final Beds beds;
    private final BedStays stays;
    private final AdmissionEventOutbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;

    public BedAssignments(Admissions admissions, Beds beds, BedStays stays, AdmissionEventOutbox outbox,
                          TransactionOperations transactions, Clock clock) {
        this.admissions = admissions;
        this.beds = beds;
        this.stays = stays;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Admission assign(UUID admissionUuid, long expectedVersion, UUID bedUuid) {
        return transactions.execute(status -> {
            Admission admission = admissions.findByUuid(admissionUuid)
                    .orElseThrow(AdmissionsException.AdmissionNotFound::new);
            requireVersion(admission.version(), expectedVersion);
            if (!admission.status().open()) {
                throw new AdmissionsException.ClosedAdmission();
            }
            if (admission.occupiesABed() && admission.bedUuid().equals(bedUuid)) {
                return admission;
            }
            if (admission.occupiesABed()) {
                freeCurrentBed(admission);
            }
            Bed bed = beds.lockByUuid(bedUuid).orElseThrow(AdmissionsException.BedNotFound::new);
            bed.occupy(admissionUuid, clock);
            try {
                stays.save(BedStay.begin(bed, admissionUuid, Instant.now(clock)));
            } catch (DataIntegrityViolationException taken) {
                log.warn("Bed {} was taken by another admission first", bedUuid);
                throw new AdmissionsException.BedAlreadyTaken();
            }
            beds.save(bed);
            admission.assignBed(bedUuid);
            publish(admissions.save(admission));
            log.info("Admission {} took bed {}", admission.number(), bedUuid);
            return reloaded(admissionUuid);
        });
    }

    public Admission release(UUID admissionUuid, long expectedVersion) {
        return transactions.execute(status -> {
            Admission admission = admissions.findByUuid(admissionUuid)
                    .orElseThrow(AdmissionsException.AdmissionNotFound::new);
            requireVersion(admission.version(), expectedVersion);
            freeCurrentBed(admission);
            publish(admissions.save(admission));
            return reloaded(admissionUuid);
        });
    }

    void freeCurrentBed(Admission admission) {
        UUID released = admission.releaseBed();
        Bed bed = beds.lockByUuid(released).orElseThrow(AdmissionsException.BedNotFound::new);
        BedStay stay = stays.findOpenByBed(released).orElseThrow(AdmissionsException.BedNotOccupied::new);
        bed.release(clock);
        stay.end(clock);
        stays.save(stay);
        beds.save(bed);
        log.info("Admission {} left bed {}", admission.number(), released);
    }

    private void publish(Admission admission) {
        List<AdmissionEvent> events = admission.pullEvents();
        if (!events.isEmpty()) {
            outbox.append(admission, events);
        }
    }

    private Admission reloaded(UUID uuid) {
        return admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new EntityTags.StaleVersion();
        }
    }
}
