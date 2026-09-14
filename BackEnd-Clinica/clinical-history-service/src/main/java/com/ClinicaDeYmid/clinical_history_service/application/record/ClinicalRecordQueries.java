package com.ClinicaDeYmid.clinical_history_service.application.record;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounters;
import com.ClinicaDeYmid.clinical_history_service.domain.note.ClinicalNotes;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDrafts;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClinicalRecordQueries {

    private final PatientDirectory patients;
    private final Encounters encounters;
    private final ClinicalNotes notes;
    private final NoteDrafts drafts;
    private final RecordAccess access;

    public ClinicalRecordQueries(PatientDirectory patients, Encounters encounters, ClinicalNotes notes, NoteDrafts drafts,
                                 RecordAccess access) {
        this.patients = patients;
        this.encounters = encounters;
        this.notes = notes;
        this.drafts = drafts;
        this.access = access;
    }

    public record NoteEntry(SignedNote note, Optional<NoteVoid> voiding) {
    }

    public record EncounterRecord(Encounter encounter, List<NoteEntry> notes) {
    }

    public EncounterRecord encounter(UUID encounterId, Clinician reader) {
        Encounter encounter = encounters.find(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
        access.requirePatient(reader, encounter.patientUuid(), AccessAction.READ_ENCOUNTER, encounterId);
        Map<UUID, NoteVoid> voids = notes.voidsInEncounter(encounterId).stream()
                .collect(Collectors.toMap(NoteVoid::noteId, Function.identity()));
        List<NoteEntry> entries = notes.ofEncounter(encounterId).stream()
                .map(note -> new NoteEntry(note, Optional.ofNullable(voids.get(note.id()))))
                .toList();
        return new EncounterRecord(encounter, entries);
    }

    public List<Encounter> encountersOf(UUID patientUuid, int page, int size, Clinician reader) {
        access.requirePatient(reader, patientUuid, AccessAction.LIST_ENCOUNTERS, patientUuid);
        return encounters.ofSubjects(patients.subjectsOf(patientUuid), page, size);
    }

    public NoteEntry note(UUID noteId, Clinician reader) {
        SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
        UUID patientUuid = encounters.find(note.encounterId()).orElseThrow(ClinicalException.EncounterNotFound::new).patientUuid();
        access.requireNote(reader, patientUuid, note);
        return new NoteEntry(note, notes.voidOf(noteId));
    }

    @Transactional(readOnly = true)
    public List<NoteDraft> draftsOf(Clinician author) {
        return drafts.writtenBy(author.uuid());
    }

    @Transactional(readOnly = true)
    public NoteDraft draft(UUID draftId, Clinician author) {
        NoteDraft draft = drafts.find(draftId).orElseThrow(ClinicalException.DraftNotFound::new);
        draft.requireAuthor(author);
        return draft;
    }
}
