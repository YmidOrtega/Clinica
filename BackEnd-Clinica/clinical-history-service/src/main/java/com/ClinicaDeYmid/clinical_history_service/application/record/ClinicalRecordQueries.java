package com.ClinicaDeYmid.clinical_history_service.application.record;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
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
@Transactional(readOnly = true)
public class ClinicalRecordQueries {

    private final PatientDirectory patients;
    private final Encounters encounters;
    private final ClinicalNotes notes;
    private final NoteDrafts drafts;

    public ClinicalRecordQueries(PatientDirectory patients, Encounters encounters, ClinicalNotes notes, NoteDrafts drafts) {
        this.patients = patients;
        this.encounters = encounters;
        this.notes = notes;
        this.drafts = drafts;
    }

    public record NoteEntry(SignedNote note, Optional<NoteVoid> voiding) {
    }

    public record EncounterRecord(Encounter encounter, List<NoteEntry> notes) {
    }

    public EncounterRecord encounter(UUID encounterId) {
        Encounter encounter = encounters.find(encounterId).orElseThrow(ClinicalException.EncounterNotFound::new);
        Map<UUID, NoteVoid> voids = notes.voidsInEncounter(encounterId).stream()
                .collect(Collectors.toMap(NoteVoid::noteId, Function.identity()));
        List<NoteEntry> entries = notes.ofEncounter(encounterId).stream()
                .map(note -> new NoteEntry(note, Optional.ofNullable(voids.get(note.id()))))
                .toList();
        return new EncounterRecord(encounter, entries);
    }

    public List<Encounter> encountersOf(UUID patientUuid, int page, int size) {
        return encounters.ofSubjects(patients.subjectsOf(patientUuid), page, size);
    }

    public NoteEntry note(UUID noteId) {
        SignedNote note = notes.find(noteId).orElseThrow(ClinicalException.NoteNotFound::new);
        return new NoteEntry(note, notes.voidOf(noteId));
    }

    public List<NoteDraft> draftsOf(Clinician author) {
        return drafts.writtenBy(author.uuid());
    }

    public NoteDraft draft(UUID draftId, Clinician author) {
        NoteDraft draft = drafts.find(draftId).orElseThrow(ClinicalException.DraftNotFound::new);
        draft.requireAuthor(author);
        return draft;
    }
}
