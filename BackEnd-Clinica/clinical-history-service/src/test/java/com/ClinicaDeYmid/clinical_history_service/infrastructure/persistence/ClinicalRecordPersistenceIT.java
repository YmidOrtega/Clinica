package com.ClinicaDeYmid.clinical_history_service.infrastructure.persistence;

import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterClosure;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteDraft;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.note.TriageLevel;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.config.ClockConfiguration;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.discharge;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.progress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcPatientReferences.class, JdbcEncounters.class, JdbcClinicalNotes.class, JdbcNoteDrafts.class, ClockConfiguration.class,
        MySqlTestContainer.class})
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
    private JdbcTemplate jdbc;

    private UUID patient;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM clinical_workspace.note_drafts");
        jdbc.update("DELETE FROM clinical_ledger.note_voids");
        jdbc.update("DELETE FROM clinical_ledger.notes WHERE amends_note_id IS NOT NULL");
        jdbc.update("DELETE FROM clinical_ledger.notes");
        jdbc.update("DELETE FROM clinical_ledger.encounter_closures");
        jdbc.update("DELETE FROM clinical_ledger.encounters");
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
                "Intubación"), 2);
        SignedNote consultation = note(encounter, doctor, new NoteContent.Consultation("Neumología", "Valoración", "Neumotórax", "Tubo"), 3);
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
        NoteDraft draft = new NoteDraft(UUID.randomUUID(), encounter.id(), doctor, new NoteContent.Progress("Dolor", null, null, null),
                OPENED_AT, 0, OPENED_AT, OPENED_AT);
        drafts.add(draft);
        NoteDraft revised = new NoteDraft(draft.id(), draft.encounterId(), doctor, progress(), OPENED_AT, 1, OPENED_AT,
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
    void databaseRejectsContentThatContradictsTheNoteType() {
        Encounter encounter = encounter(EncounterType.EMERGENCY, OPENED_AT);
        encounters.add(encounter);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO clinical_ledger.notes
                    (id, encounter_id, type, content, author_uuid, author_role, author_email, occurred_at, recorded_at, extemporaneous)
                VALUES (UUID(), ?, 'DISCHARGE', '{"type": "PROGRESS"}', UUID(), 'DOCTOR', 'doctor@clinica.test', NOW(6), NOW(6), FALSE)""",
                encounter.id().toString())).hasMessageContaining("chk_notes_content_type");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO clinical_ledger.encounters (id, patient_uuid, type, opened_at, opened_by, opened_by_role)
                VALUES (?, ?, 'EMERGENCY', NOW(6), UUID(), 'DOCTOR')""", UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .hasMessageContaining("fk_encounters_patient");
    }

    private Encounter encounter(EncounterType type, Instant openedAt) {
        return new Encounter(UUID.randomUUID(), patient, type, "ADM-1", openedAt, doctor(), new EncounterStatus.Open());
    }

    private static SignedNote note(Encounter encounter, Clinician author, NoteContent content, int minute) {
        Instant at = encounter.openedAt().plus(Duration.ofMinutes(minute));
        return new SignedNote(UUID.randomUUID(), encounter.id(), author, "autor@clinica.test", content, at, at.plusSeconds(30), minute == 7);
    }
}
