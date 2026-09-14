package com.ClinicaDeYmid.clinical_history_service.application.encounter;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.application.integrity.RecordSealing;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessBasis;
import com.ClinicaDeYmid.clinical_history_service.domain.access.CareTeams;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EncounterCommands {

    private static final Logger log = LoggerFactory.getLogger(EncounterCommands.class);

    private final PatientDirectory patients;
    private final Encounters encounters;
    private final ClinicalNotes notes;
    private final CareTeams careTeams;
    private final RecordSealing sealing;
    private final RecordAccess access;
    private final TransactionOperations transactions;
    private final Clock clock;

    public EncounterCommands(PatientDirectory patients, Encounters encounters, ClinicalNotes notes, CareTeams careTeams,
                             RecordSealing sealing, RecordAccess access, TransactionOperations transactions, Clock clock) {
        this.patients = patients;
        this.encounters = encounters;
        this.notes = notes;
        this.careTeams = careTeams;
        this.sealing = sealing;
        this.access = access;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Encounter open(UUID patientUuid, EncounterType type, String admissionId, Clinician clinician) {
        PatientReference patient = switch (patients.find(patientUuid)) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound notFound -> throw new ClinicalException.PatientNotFound();
            case PatientLookup.Unavailable unavailable -> throw new ClinicalException.PatientRegistryUnavailable();
        };
        Encounter encounter = Encounter.open(patient, type, admissionId, clinician, clock);
        transactions.executeWithoutResult(status -> {
            sealing.record(new LedgerEntry.EncounterOpened(encounter));
            encounters.add(encounter);
            careTeams.add(encounter.id(), clinician, clinician, encounter.openedAt());
            access.recordGranted(clinician, patientUuid, AccessAction.OPEN_ENCOUNTER, encounter.id(), AccessBasis.NEW_ENCOUNTER, null);
        });
        log.info("Encounter {} of type {} opened by {}", encounter.id(), encounter.type(), clinician.uuid());
        return encounter;
    }

    @Transactional
    public Encounter close(UUID encounterId, Clinician clinician) {
        Encounter encounter = encounters.lock(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
        access.requireEncounter(clinician, encounter.patientUuid(), encounterId, AccessAction.CLOSE_ENCOUNTER, encounterId);
        EncounterClosure closure = encounter.close(clinician, notes.ofEncounter(encounterId),
                notes.voidsInEncounter(encounterId).stream().map(NoteVoid::noteId).collect(Collectors.toSet()), clock);
        encounters.close(closure);
        sealing.record(new LedgerEntry.EncounterClosed(encounter.patientUuid(), closure));
        log.info("Encounter {} closed by {}", encounterId, clinician.uuid());
        return new Encounter(encounter.id(), encounter.patientUuid(), encounter.type(), encounter.admissionId(),
                encounter.openedAt(), encounter.openedBy(), new EncounterStatus.Closed(closure.closedAt(), closure.closedBy()));
    }
}
