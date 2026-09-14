package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import com.ClinicaDeYmid.patient_service.domain.Sex;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class UnidentifiedPatientCommands {

    private static final Logger log = LoggerFactory.getLogger(UnidentifiedPatientCommands.class);

    private final UnidentifiedPatients unidentifiedPatients;
    private final Patients patients;
    private final PatientCommands patientCommands;
    private final PatientEventOutbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;

    public UnidentifiedPatientCommands(UnidentifiedPatients unidentifiedPatients, Patients patients, PatientCommands patientCommands,
                                       PatientEventOutbox outbox, TransactionOperations transactions, Clock clock) {
        this.unidentifiedPatients = unidentifiedPatients;
        this.patients = patients;
        this.patientCommands = patientCommands;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    public UnidentifiedPatient register(Sex sex, int estimatedBirthYear, String description) {
        UnidentifiedPatient registered = transactions.execute(status -> {
            String code = unidentifiedPatients.nextCode(LocalDate.now(clock).getYear());
            return saveWithEvents(UnidentifiedPatient.register(code, sex, estimatedBirthYear, description, clock));
        });
        log.info("Unidentified patient registered: uuid={} code={}", registered.uuid(), registered.code());
        return registered;
    }

    public UnidentifiedPatient identifyAsExisting(UUID uuid, long expectedVersion, UUID patientUuid, String reason) {
        return modify(uuid, expectedVersion, unidentified -> {
            Patient patient = patients.findByUuid(patientUuid).orElseThrow(PatientException.NotFound::new);
            unidentified.identifyAs(patient, reason, clock);
        });
    }

    public UnidentifiedPatient identifyAsNew(UUID uuid, long expectedVersion, PatientRegistration registration, String reason) {
        patientCommands.verifyHealthProvider(registration.affiliation());
        return modify(uuid, expectedVersion, unidentified -> {
            Patient patient = patientCommands.registerInCurrentTransaction(registration);
            unidentified.identifyAs(patient, reason, clock);
        });
    }

    public UnidentifiedPatient revertIdentification(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, unidentified -> unidentified.revertIdentification(reason, clock));
    }

    public UnidentifiedPatient recordDeath(UUID uuid, long expectedVersion, LocalDate dateOfDeath) {
        return modify(uuid, expectedVersion, unidentified -> unidentified.recordDeath(dateOfDeath, clock));
    }

    private UnidentifiedPatient modify(UUID uuid, long expectedVersion, Consumer<UnidentifiedPatient> change) {
        return transactions.execute(status -> {
            UnidentifiedPatient unidentified = unidentifiedPatients.findByUuid(uuid)
                    .orElseThrow(PatientException.UnidentifiedNotFound::new);
            if (unidentified.version() != expectedVersion) {
                throw new ApplicationException.StaleVersion();
            }
            change.accept(unidentified);
            return saveWithEvents(unidentified);
        });
    }

    private UnidentifiedPatient saveWithEvents(UnidentifiedPatient unidentified) {
        List<UnidentifiedPatientEvent> events = unidentified.pullEvents();
        UnidentifiedPatient saved = unidentifiedPatients.save(unidentified);
        if (!events.isEmpty()) {
            outbox.appendUnidentified(saved, events);
        }
        return saved;
    }
}
