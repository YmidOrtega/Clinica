package com.ClinicaDeYmid.billing_service.application.clinical;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CareRecord(List<Encounter> encounters, List<Note> notes) {

    public record Encounter(UUID id, String type, Instant openedAt, Instant closedAt,
                            ClinicalFact.CareSetting careSetting) {
    }

    public record Note(UUID id, UUID encounterId, String type, Instant careOccurredAt, String purpose, String cause,
                       List<ClinicalFact.CodedDiagnosis> diagnoses) {

        public Optional<ClinicalFact.CodedDiagnosis> principal() {
            return diagnoses.stream().filter(ClinicalFact.CodedDiagnosis::principal).findFirst();
        }

        public List<ClinicalFact.CodedDiagnosis> related() {
            return diagnoses.stream().filter(diagnosis -> !diagnosis.principal()).toList();
        }
    }

    public CareRecord {
        encounters = encounters.stream().sorted(Comparator.comparing(Encounter::openedAt)).toList();
        notes = notes.stream().sorted(Comparator.comparing(Note::careOccurredAt)).toList();
    }

    public static CareRecord empty() {
        return new CareRecord(List.of(), List.of());
    }

    public Optional<Encounter> encounterAt(Instant moment) {
        return encounters.stream().filter(encounter -> !encounter.openedAt().isAfter(moment))
                .reduce((first, second) -> second)
                .or(() -> encounters.stream().findFirst());
    }

    public List<Note> notesOf(UUID encounterId) {
        return notes.stream().filter(note -> note.encounterId().equals(encounterId)).toList();
    }

    public Optional<Note> firstDiagnosed() {
        return notes.stream().filter(note -> note.principal().isPresent()).findFirst();
    }

    public Optional<Note> lastDiagnosed() {
        return notes.stream().filter(note -> note.principal().isPresent()).reduce((first, second) -> second);
    }
}
