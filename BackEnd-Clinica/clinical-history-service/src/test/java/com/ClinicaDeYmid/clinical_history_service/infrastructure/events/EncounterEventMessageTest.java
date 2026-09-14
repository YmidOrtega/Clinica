package com.ClinicaDeYmid.clinical_history_service.infrastructure.events;

import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.support.AccessAuditContract;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.openEncounter;
import static org.assertj.core.api.Assertions.assertThat;

class EncounterEventMessageTest {

    private static final Instant AT = Instant.parse("2026-09-14T15:00:00.123456Z");

    @Test
    void everySealedFactFollowsThePublishedContractWithoutNoteText() {
        Encounter encounter = openEncounter(EncounterType.EMERGENCY);
        SignedNote note = new SignedNote(UUID.randomUUID(), encounter.id(), doctor(), "doctor@clinica.test",
                new NoteContent.Discharge("Ingresa por cefalea", "Mejoría", "Estable", "Reposo", "Control",
                        List.of(new Diagnosis("I10X", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.CONFIRMED_NEW, "Hipertension esencial", "2021-02-08"))),
                NoteRestriction.MENTAL_HEALTH, List.of(), List.of(), AT, AT.plusSeconds(60), false);
        List<LedgerEntry> entries = List.of(
                new LedgerEntry.EncounterOpened(encounter),
                new LedgerEntry.NoteSigned(encounter.patientUuid(), note),
                new LedgerEntry.NoteVoided(encounter.patientUuid(), new NoteVoid(note.id(), "Paciente equivocado", doctor(), AT.plusSeconds(90))),
                new LedgerEntry.EncounterClosed(encounter.patientUuid(), new EncounterClosure(encounter.id(), AT.plusSeconds(120), doctor())));

        for (int index = 0; index < entries.size(); index++) {
            LedgerEntry entry = entries.get(index);
            ChainLink link = new ChainLink(entry.patientUuid(), index + 1, entry.type(), entry.entryId(), 1, "a".repeat(64),
                    index == 0 ? ChainLink.GENESIS_HASH : "b".repeat(64), "c".repeat(64), "seal-2026", "c2VhbA==", AT);
            String json = JdbcClinicalEventOutbox.write(EncounterEventMessage.of(entry, link, UUID.randomUUID(), null));

            assertThat(AccessAuditContract.encounterEventViolations(json)).as(json).isEmpty();
            assertThat(json).doesNotContain("cefalea").doesNotContain("Paciente equivocado").doesNotContain("doctor@clinica.test");
        }
        assertThat(JdbcClinicalEventOutbox.write(EncounterEventMessage.of(entries.get(1), new ChainLink(encounter.patientUuid(), 2,
                EntryType.NOTE_SIGNED, note.id(), 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "k", "s", AT), UUID.randomUUID(), null)))
                .contains("\"restricted\":true").contains("\"code\":\"I10X\"");
    }
}
