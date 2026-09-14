package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.note.TriageLevel;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptionConfiguration;
import com.ClinicaDeYmid.clinical_history_service.support.ClinicalTestProperties;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.discharge;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.progress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcPatientReferences.class, JdbcEncounters.class, JdbcClinicalNotes.class, JdbcNoteDrafts.class, JdbcChainLinks.class,
        JdbcLedgerEntries.class, JdbcPatientChart.class, ClockConfiguration.class, EncryptionConfiguration.class, MySqlTestContainer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClinicalRecordPersistenceIT {

    private static final Instant OPENED_AT = Instant.parse("2026-09-10T14:00:00.123456Z");

    @Autowired
    private JdbcPatientReferences patients;

    @Autowired
    private JdbcEncounters encounters;

    @Autowired
    private JdbcClinicalNotes notes;

    @Autowired
    private JdbcNoteDrafts drafts;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcChainLinks chainLinks;

    @Autowired
    private JdbcLedgerEntries ledgerEntries;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID patient;

    @DynamicPropertySource
    static void encryptionKeys(DynamicPropertyRegistry registry) {
        ClinicalTestProperties.encryption(registry);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM clinical_ledger.chain_links");
        jdbc.update("DELETE FROM clinical_workspace.note_drafts");
        jdbc.update("DELETE FROM clinical_ledger.note_voids");
        jdbc.update("DELETE FROM clinical_ledger.notes WHERE amends_note_id IS NOT NULL");
        jdbc.update("DELETE FROM clinical_ledger.notes");
        jdbc.update("DELETE FROM clinical_ledger.encounter_closures");
        jdbc.update("DELETE FROM clinical_ledger.encounters");
        jdbc.update("DELETE FROM clinical_keys.data_key_wrappings");
        jdbc.update("DELETE FROM clinical_keys.data_keys");
        patient = UUID.randomUUID();
        patients.saveIfNewer(PatientReferencesPersistenceIT.registered(patient, 0, PatientReference.Registered.Status.ACTIVE));
    }

    @Test
    void storesEncountersAndTheirClosure() {
        Encounter encounter = encounter(EncounterType.INPATIENT, OPENED_AT);
        encounters.add(encounter);

        assertThat(encounters.find(encounter.id())).contains(encounter);

        Clinician closer = doctor();
        encounters.close(new EncounterClosure(encounter.id(), OPENED_AT.plus(Duration.ofDays(3)), closer));

        assertThat(encounters.lock(encounter.id())).hasValueSatisfying(stored -> assertThat(stored.status())
                .isEqualTo(new EncounterStatus.Closed(OPENED_AT.plus(Duration.ofDays(3)), closer)));
        assertThat(encounters.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listsTheEncountersOfEverySubjectNewestFirstByPage() {
        Encounter older = encounter(EncounterType.EMERGENCY, OPENED_AT);
        Encounter newer = encounter(EncounterType.OUTPATIENT, OPENED_AT.plus(Duration.ofDays(30)));
        encounters.add(older);
        encounters.add(newer);

        assertThat(encounters.ofSubjects(List.of(patient, UUID.randomUUID()), 0, 20)).containsExactly(newer, older);
        assertThat(encounters.ofSubjects(List.of(patient), 1, 1)).containsExactly(older);
        assertThat(encounters.ofSubjects(List.of(), 0, 20)).isEmpty();
    }

    @Test
    void storesSignedNotesOfEveryTypeWithTheirVoiding() {
        Encounter encounter = encounter(EncounterType.EMERGENCY, OPENED_AT);
        encounters.add(encounter);
        Clinician doctor = doctor();
        SignedNote triage = note(encounter, nurse(), new NoteContent.Triage(TriageLevel.I, "Paro respiratorio", "Ingresa en camilla"), 1);
        SignedNote admission = note(encounter, doctor, new NoteContent.Admission("Disnea", "Inicio súbito", "Cianosis", "Falla respiratoria",
                "Intubación", List.of()), 2);
        SignedNote consultation = note(encounter, doctor, new NoteContent.Consultation("Neumología", "Valoración", "Neumotórax", "Tubo", List.of()), 3);
        SignedNote nursing = note(encounter, nurse(), new NoteContent.Nursing("Saturación 94 %", "Aspiración de secreciones"), 4);
        SignedNote evolution = note(encounter, doctor, progress(), 5);
        SignedNote summary = note(encounter, doctor, discharge(), 6);
        SignedNote addendum = note(encounter, doctor, new NoteContent.Addendum(evolution.id(), "La TA fue 130/80"), 7);
        List<SignedNote> all = List.of(triage, admission, consultation, nursing, evolution, summary, addendum);
        all.forEach(notes::append);

        NoteVoid voiding = new NoteVoid(consultation.id(), "Registrada en la atención equivocada", doctor, OPENED_AT.plusSeconds(3600));

        assertThat(notes.addVoid(voiding)).isTrue();
        assertThat(notes.addVoid(voiding)).isFalse();
        assertThat(notes.ofEncounter(encounter.id())).containsExactlyElementsOf(all);
        assertThat(jdbc.queryForList("SELECT content_ciphertext FROM clinical_ledger.notes", byte[].class))
                .hasSize(7)
                .allSatisfy(ciphertext -> assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1))
                        .doesNotContain("Neumotórax").doesNotContain("Paro respiratorio").doesNotContain("\"type\""));
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT content_key_id) FROM clinical_ledger.notes", Long.class)).isEqualTo(1);
        assertThat(notes.find(addendum.id())).hasValueSatisfying(stored -> assertThat(stored.amends()).contains(evolution.id()));
        assertThat(notes.voidOf(consultation.id())).contains(voiding);
        assertThat(notes.voidsInEncounter(encounter.id())).containsExactly(voiding);
        assertThat(jdbc.queryForObject("SELECT amends_note_id FROM clinical_ledger.notes WHERE id = ?", String.class,
                addendum.id().toString())).isEqualTo(evolution.id().toString());
    }

    @Test
    void replacesDraftsOnlyFromTheExpectedVersion() {
        Encounter encounter = encounter(EncounterType.EMERGENCY, OPENED_AT);
        encounters.add(encounter);
        Clinician doctor = doctor();
        NoteDraft draft = new NoteDraft(UUID.randomUUID(), encounter.id(), doctor, new NoteContent.Progress("Dolor", null, null, null, List.of()), null, List.of(),
                OPENED_AT, 0, OPENED_AT, OPENED_AT);
        drafts.add(draft);
        NoteDraft revised = new NoteDraft(draft.id(), draft.encounterId(), doctor, progress(), NoteRestriction.MENTAL_HEALTH, List.of(), OPENED_AT, 1, OPENED_AT,
                OPENED_AT.plusSeconds(60));

        assertThat(drafts.replace(revised, 1)).isFalse();
        assertThat(drafts.replace(revised, 0)).isTrue();
        assertThat(drafts.lock(draft.id())).contains(revised);
        assertThat(drafts.writtenBy(doctor.uuid())).containsExactly(revised);
        assertThat(drafts.writtenBy(UUID.randomUUID())).isEmpty();

        drafts.remove(draft.id());

        assertThat(drafts.find(draft.id())).isEmpty();
    }

    @Test
    void rebuildsEveryLedgerEntryOfAPatientFromTheStoredRows() {
        Encounter encounter = encounter(EncounterType.OUTPATIENT, OPENED_AT);
        encounters.add(encounter);
        SignedNote evolution = note(encounter, doctor(), progress(), 1);
        notes.append(evolution);
        NoteVoid voiding = new NoteVoid(evolution.id(), "Error", evolution.author(), OPENED_AT.plusSeconds(600));
        notes.addVoid(voiding);
        EncounterClosure closure = new EncounterClosure(encounter.id(), OPENED_AT.plusSeconds(900), doctor());
        encounters.close(closure);

        assertThat(ledgerEntries.recordedFor(patient)).containsExactly(
                new LedgerEntry.EncounterOpened(encounters.find(encounter.id()).orElseThrow()),
                new LedgerEntry.EncounterClosed(patient, closure),
                new LedgerEntry.NoteSigned(patient, evolution),
                new LedgerEntry.NoteVoided(patient, voiding));
        assertThat(ledgerEntries.recordedFor(UUID.randomUUID())).isEmpty();
    }

    @Test
    void appendsChainLinksAndFindsTheHeadUnderLock() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ChainLink genesis = link(1, EntryType.ENCOUNTER_OPENED, first, ChainLink.GENESIS_HASH, "a".repeat(64));
        ChainLink next = link(2, EntryType.NOTE_SIGNED, second, "a".repeat(64), "b".repeat(64));

        assertThat(lockHead(patient)).isEmpty();
        chainLinks.append(genesis);
        chainLinks.append(next);

        assertThat(lockHead(patient)).contains(next);
        assertThat(chainLinks.chainOf(patient)).containsExactly(genesis, next);
        assertThat(chainLinks.linkOf(new LedgerEntry.Key(EntryType.NOTE_SIGNED, second))).contains(next);
        assertThatThrownBy(() -> chainLinks.append(link(3, EntryType.NOTE_SIGNED, second, "b".repeat(64), "c".repeat(64))))
                .hasMessageContaining("uk_chain_links_entry");
        assertThatThrownBy(() -> chainLinks.append(link(3, EntryType.NOTE_VOIDED, second, "a".repeat(64), "c".repeat(64))))
                .hasMessageContaining("uk_chain_links_previous_hash");
        assertThatThrownBy(() -> lockHead(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseRejectsEncountersOfUnknownPatients() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO clinical_ledger.encounters (id, patient_uuid, type, opened_at, opened_by, opened_by_role)
                VALUES (?, ?, 'EMERGENCY', NOW(6), UUID(), 'DOCTOR')""", UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .hasMessageContaining("fk_encounters_patient");
    }

    private Optional<ChainLink> lockHead(UUID patientUuid) {
        return transactions.execute(status -> chainLinks.lockHead(patientUuid));
    }

    private ChainLink link(long sequence, EntryType type, UUID entryId, String previousHash, String entryHash) {
        return new ChainLink(patient, sequence, type, entryId, 1, "d".repeat(64), previousHash, entryHash, "seal-2026", "c2VhbA==",
                OPENED_AT.plusSeconds(sequence));
    }

    private Encounter encounter(EncounterType type, Instant openedAt) {
        return new Encounter(UUID.randomUUID(), patient, type, "ADM-1", openedAt, doctor(), new EncounterStatus.Open());
    }

    private static SignedNote note(Encounter encounter, Clinician author, NoteContent content, int minute) {
        Instant at = encounter.openedAt().plus(Duration.ofMinutes(minute));
        return new SignedNote(UUID.randomUUID(), encounter.id(), author, "autor@clinica.test", content, minute == 4 ? NoteRestriction.VIOLENCE : null, List.of(), at, at.plusSeconds(30), minute == 7);
    }
}
