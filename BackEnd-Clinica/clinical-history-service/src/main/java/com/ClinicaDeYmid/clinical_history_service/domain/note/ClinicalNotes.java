package com.ClinicaDeYmid.clinical_history_service.domain.note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalNotes {

    void append(SignedNote note);

    Optional<SignedNote> find(UUID id);

    List<SignedNote> ofEncounter(UUID encounterId);

    boolean addVoid(NoteVoid noteVoid);

    Optional<NoteVoid> voidOf(UUID noteId);

    List<NoteVoid> voidsInEncounter(UUID encounterId);
}
