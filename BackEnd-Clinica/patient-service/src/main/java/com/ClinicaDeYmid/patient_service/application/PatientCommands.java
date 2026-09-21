package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Affiliation;
import com.ClinicaDeYmid.patient_service.domain.ContactInfo;
import com.ClinicaDeYmid.patient_service.domain.Demographics;
import com.ClinicaDeYmid.patient_service.domain.EmergencyContact;
import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.PatientRegistration;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import com.ClinicaDeYmid.patient_service.domain.Residence;
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
public class PatientCommands {

    private static final Logger log = LoggerFactory.getLogger(PatientCommands.class);

    private final Patients patients;
    private final PayerDirectory payers;
    private final PatientEventOutbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;

    public PatientCommands(Patients patients, PayerDirectory payers, PatientEventOutbox outbox,
                           TransactionOperations transactions, Clock clock) {
        this.patients = patients;
        this.payers = payers;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Patient register(PatientRegistration registration) {
        verifyPayer(registration.affiliation());
        return transactions.execute(status -> registerInCurrentTransaction(registration));
    }

    Patient registerInCurrentTransaction(PatientRegistration registration) {
        if (patients.existsByDocument(registration.document())) {
            throw new PatientException.DocumentAlreadyRegistered();
        }
        Patient registered = saveWithEvents(Patient.register(registration, clock));
        log.info("Patient registered: uuid={}", registered.uuid());
        return registered;
    }

    public Patient changeDocument(UUID uuid, long expectedVersion, IdentityDocument document) {
        return modify(uuid, expectedVersion, patient -> {
            patients.findByDocument(document)
                    .filter(other -> !other.equals(patient))
                    .ifPresent(other -> {
                        throw new PatientException.DocumentAlreadyRegistered();
                    });
            patient.changeDocument(document, clock);
        });
    }

    public Patient correctDemographics(UUID uuid, long expectedVersion, Demographics demographics) {
        return modify(uuid, expectedVersion, patient -> patient.correctDemographics(demographics, clock));
    }

    public Patient updateContact(UUID uuid, long expectedVersion, ContactInfo contact, EmergencyContact emergencyContact) {
        return modify(uuid, expectedVersion, patient -> patient.updateContact(contact, emergencyContact, clock));
    }

    public Patient updateAffiliation(UUID uuid, long expectedVersion, Affiliation affiliation) {
        verifyPayer(affiliation);
        return modify(uuid, expectedVersion, patient -> patient.updateAffiliation(affiliation));
    }

    public Patient updateResidence(UUID uuid, long expectedVersion, Residence residence) {
        return modify(uuid, expectedVersion, patient -> patient.updateResidence(residence));
    }

    public Patient deactivate(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, patient -> patient.deactivate(reason, clock));
    }

    public Patient reactivate(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, patient -> patient.reactivate(clock));
    }

    public Patient recordDeath(UUID uuid, long expectedVersion, LocalDate dateOfDeath) {
        return modify(uuid, expectedVersion, patient -> patient.recordDeath(dateOfDeath, clock));
    }

    private Patient modify(UUID uuid, long expectedVersion, Consumer<Patient> change) {
        return transactions.execute(status -> {
            Patient patient = patients.findByUuid(uuid).orElseThrow(PatientException.NotFound::new);
            if (patient.version() != expectedVersion) {
                throw new ApplicationException.StaleVersion();
            }
            change.accept(patient);
            return saveWithEvents(patient);
        });
    }

    Patient saveWithEvents(Patient patient) {
        List<PatientEvent> events = patient.pullEvents();
        Patient saved = patients.save(patient);
        if (!events.isEmpty()) {
            outbox.append(saved, events);
        }
        return saved;
    }

    void verifyPayer(Affiliation affiliation) {
        if (affiliation == null || !affiliation.regime().hasPayer()) {
            return;
        }
        switch (payers.findByUuid(affiliation.payerUuid())) {
            case PayerLookup.Found found -> {
            }
            case PayerLookup.NotFound notFound -> throw new ApplicationException.PayerNotFound();
            case PayerLookup.Unavailable unavailable -> throw new ApplicationException.PayerUnavailable();
            case PayerLookup.NotAffiliated notAffiliated -> {
            }
        }
    }
}
