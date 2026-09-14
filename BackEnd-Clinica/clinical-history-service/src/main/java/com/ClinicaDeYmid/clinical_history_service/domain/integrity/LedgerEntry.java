package com.ClinicaDeYmid.clinical_history_service.domain.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;

import java.util.Objects;
import java.util.UUID;

public sealed interface LedgerEntry {

    UUID patientUuid();

    EntryType type();

    UUID entryId();

    default Key key() {
        return new Key(type(), entryId());
    }

    record Key(EntryType type, UUID entryId) {
    }

    record EncounterOpened(Encounter encounter) implements LedgerEntry {

        public EncounterOpened {
            Objects.requireNonNull(encounter, "encounter");
        }

        @Override
        public UUID patientUuid() {
            return encounter.patientUuid();
        }

        @Override
        public EntryType type() {
            return EntryType.ENCOUNTER_OPENED;
        }

        @Override
        public UUID entryId() {
            return encounter.id();
        }
    }

    record NoteSigned(UUID patientUuid, SignedNote note) implements LedgerEntry {

        public NoteSigned {
            Objects.requireNonNull(patientUuid, "patientUuid");
            Objects.requireNonNull(note, "note");
        }

        @Override
        public EntryType type() {
            return EntryType.NOTE_SIGNED;
        }

        @Override
        public UUID entryId() {
            return note.id();
        }
    }

    record NoteVoided(UUID patientUuid, NoteVoid noteVoid) implements LedgerEntry {

        public NoteVoided {
            Objects.requireNonNull(patientUuid, "patientUuid");
            Objects.requireNonNull(noteVoid, "noteVoid");
        }

        @Override
        public EntryType type() {
            return EntryType.NOTE_VOIDED;
        }

        @Override
        public UUID entryId() {
            return noteVoid.noteId();
        }
    }

    record EncounterClosed(UUID patientUuid, EncounterClosure closure) implements LedgerEntry {

        public EncounterClosed {
            Objects.requireNonNull(patientUuid, "patientUuid");
            Objects.requireNonNull(closure, "closure");
        }

        @Override
        public EntryType type() {
            return EntryType.ENCOUNTER_CLOSED;
        }

        @Override
        public UUID entryId() {
            return closure.encounterId();
        }
    }
}
