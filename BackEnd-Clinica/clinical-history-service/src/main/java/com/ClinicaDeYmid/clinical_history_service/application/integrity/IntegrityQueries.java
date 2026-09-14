package com.ClinicaDeYmid.clinical_history_service.application.integrity;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLinks;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ClinicalSignature;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.IntegrityProblem;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntries;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IntegrityQueries {

    private final PatientDirectory patients;
    private final ChainLinks links;
    private final LedgerEntries entries;
    private final ClinicalNotes notes;
    private final Encounters encounters;
    private final ClinicalSignature signature;

    public IntegrityQueries(PatientDirectory patients, ChainLinks links, LedgerEntries entries, ClinicalNotes notes,
                            Encounters encounters, ClinicalSignature signature) {
        this.patients = patients;
        this.links = links;
        this.entries = entries;
        this.notes = notes;
        this.encounters = encounters;
        this.signature = signature;
    }

    public record NoteSignature(SignedNote note, Optional<ChainLink> link, Set<IntegrityProblem.Kind> problems) {

        public boolean verified() {
            return link.isPresent() && problems.isEmpty();
        }
    }

    public List<ChainVerification> verifyPatient(UUID patientUuid) {
        return patients.subjectsOf(patientUuid).stream()
                .map(subject -> ChainVerification.of(subject, links.chainOf(subject), entries.recordedFor(subject), signature))
                .toList();
    }

    public NoteSignature noteSignature(UUID noteId) {
        SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
        UUID patientUuid = encounters.find(note.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new).patientUuid();
        LedgerEntry entry = new LedgerEntry.NoteSigned(patientUuid, note);
        Optional<ChainLink> link = links.linkOf(entry.key());
        Set<IntegrityProblem.Kind> problems = link
                .map(found -> found.patientUuid().equals(patientUuid)
                        ? signature.check(entry, found)
                        : Set.of(IntegrityProblem.Kind.MISSING_ENTRY))
                .orElse(Set.of(IntegrityProblem.Kind.UNSEALED_ENTRY));
        return new NoteSignature(note, link, problems);
    }
}
