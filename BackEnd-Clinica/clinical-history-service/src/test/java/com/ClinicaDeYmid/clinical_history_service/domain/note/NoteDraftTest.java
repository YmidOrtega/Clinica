package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.OPENED_AT;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.POLICY;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.clockAt;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.closed;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.discharge;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nursing;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.openEncounter;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.progress;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.signerAt;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.triage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteDraftTest {

    private static final Instant NOW = OPENED_AT.plus(Duration.ofHours(1));

    private final Encounter encounter = openEncounter(EncounterType.EMERGENCY);

    @Test
    void startsWithTheCurrentTimeAsOccurrenceByDefault() {
        Clinician doctor = doctor();

        NoteDraft draft = NoteDraft.start(encounter, doctor, progress(), null, null, POLICY, clockAt(NOW));

        assertThat(draft.occurredAt()).isEqualTo(NOW);
        assertThat(draft.version()).isZero();
        assertThat(draft.type()).isEqualTo(NoteType.PROGRESS);
        assertThat(draft.isWrittenBy(doctor)).isTrue();
    }

    @Test
    void restrictsNoteTypesByClinicalProfile() {
        assertThatThrownBy(() -> NoteDraft.start(encounter, nurse(), progress(), null, null, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.NoteTypeNotAllowed.class);
        assertThatThrownBy(() -> NoteDraft.start(encounter, doctor(), nursing(), null, null, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.NoteTypeNotAllowed.class);
        assertThat(NoteDraft.start(encounter, nurse(), triage(), null, null, POLICY, clockAt(NOW))).isNotNull();
        assertThat(NoteDraft.start(encounter, doctor(), triage(), null, null, POLICY, clockAt(NOW))).isNotNull();
    }

    @Test
    void closedEncountersOnlyAcceptAddenda() {
        Encounter closedEncounter = closed(encounter);

        assertThatThrownBy(() -> NoteDraft.start(closedEncounter, doctor(), progress(), null, null, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.EncounterClosed.class);
        assertThat(NoteDraft.start(closedEncounter, doctor(), new NoteContent.Addendum(UUID.randomUUID(), "Aclaración"), null, null,
                POLICY, clockAt(NOW))).isNotNull();
    }

    @Test
    void addendaMustNameTheAmendedNote() {
        assertThatThrownBy(() -> NoteDraft.start(encounter, doctor(), new NoteContent.Addendum(null, "Aclaración"), null, null, POLICY,
                clockAt(NOW)))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("amendsNoteId");
    }

    @Test
    void occurrenceCannotBeInTheFutureNorBeforeTheEncounter() {
        Clinician doctor = doctor();

        assertThatThrownBy(() -> NoteDraft.start(encounter, doctor, progress(), null, NOW.plus(Duration.ofMinutes(5)), POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.InvalidOccurrence.class)
                .hasMessageContaining("futuro");
        assertThatThrownBy(() -> NoteDraft.start(encounter, doctor, progress(), null, OPENED_AT.minus(Duration.ofMinutes(5)), POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.InvalidOccurrence.class)
                .hasMessageContaining("apertura");
        assertThat(NoteDraft.start(encounter, doctor, progress(), null, NOW.plus(Duration.ofMinutes(1)), POLICY, clockAt(NOW))).isNotNull();
    }

    @Test
    void revisionKeepsTheTypeAndIncrementsTheVersion() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, new NoteContent.Progress("Dolor", null, null, null), null, null, POLICY, clockAt(NOW));

        NoteDraft revised = draft.revise(doctor, encounter, progress(), null, null, POLICY, clockAt(NOW.plusSeconds(60)));

        assertThat(revised.version()).isEqualTo(1);
        assertThat(revised.content()).isEqualTo(progress());
        assertThat(revised.occurredAt()).isEqualTo(draft.occurredAt());
        assertThat(revised.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> draft.revise(doctor, encounter, discharge(), null, null, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.DraftTypeChange.class);
    }

    @Test
    void revisionCanMarkOrUnmarkTheNoteAsRestricted() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, progress(), null, null, POLICY, clockAt(NOW));

        NoteDraft restricted = draft.revise(doctor, encounter, progress(), NoteRestriction.MENTAL_HEALTH, null, POLICY, clockAt(NOW));
        SignedNote signed = restricted.sign(signerAt(doctor, NOW), encounter, POLICY, clockAt(NOW));

        assertThat(draft.restriction()).isNull();
        assertThat(signed.restriction()).isEqualTo(NoteRestriction.MENTAL_HEALTH);
        assertThat(signed.isRestricted()).isTrue();
    }

    @Test
    void addendaCannotSwitchTheAmendedNote() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, new NoteContent.Addendum(UUID.randomUUID(), "Aclaración"), null, null, POLICY,
                clockAt(NOW));

        assertThatThrownBy(() -> draft.revise(doctor, encounter, new NoteContent.Addendum(UUID.randomUUID(), "Otra"), null, null, POLICY,
                clockAt(NOW))).isInstanceOf(ClinicalException.DraftTypeChange.class);
    }

    @Test
    void draftsAreInvisibleToEveryoneButTheirAuthor() {
        NoteDraft draft = NoteDraft.start(encounter, doctor(), progress(), null, null, POLICY, clockAt(NOW));

        assertThatThrownBy(() -> draft.revise(doctor(), encounter, progress(), null, null, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.DraftNotFound.class);
        assertThatThrownBy(() -> draft.sign(signerAt(doctor(), NOW), encounter, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.DraftNotFound.class);
    }

    @Test
    void signingRequiresEveryMandatoryField() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, new NoteContent.Progress("Dolor", " ", null, "Plan"), null, null, POLICY,
                clockAt(NOW));

        assertThatThrownBy(() -> draft.sign(signerAt(doctor, NOW), encounter, POLICY, clockAt(NOW)))
                .isInstanceOfSatisfying(ClinicalException.NoteIncomplete.class,
                        incomplete -> assertThat(incomplete.missingFields()).containsExactly("objective", "assessment"));
    }

    @Test
    void signingRecordsWhenTheNoteWasWrittenAndFlagsLateRecords() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, progress(), null, OPENED_AT, POLICY, clockAt(NOW));
        Instant dayLater = OPENED_AT.plus(Duration.ofHours(24));

        SignedNote onTime = draft.sign(signerAt(doctor, dayLater), encounter, POLICY, clockAt(dayLater));
        SignedNote late = draft.sign(signerAt(doctor, dayLater), encounter, POLICY, clockAt(dayLater.plusSeconds(1)));

        assertThat(onTime.id()).isEqualTo(draft.id());
        assertThat(onTime.signerEmail()).isEqualTo("doctor@clinica.test");
        assertThat(onTime.recordedAt()).isEqualTo(OPENED_AT.plus(Duration.ofHours(24)));
        assertThat(onTime.extemporaneous()).isFalse();
        assertThat(late.extemporaneous()).isTrue();
    }

    @Test
    void signingRequiresASessionIssuedLessThanFifteenMinutesAgo() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, progress(), null, null, POLICY, clockAt(NOW));

        assertThatThrownBy(() -> draft.sign(signerAt(doctor, NOW.minus(Duration.ofMinutes(16))), encounter, POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.RecentAuthenticationRequired.class);
        assertThat(draft.sign(signerAt(doctor, NOW.minus(Duration.ofMinutes(15))), encounter, POLICY, clockAt(NOW))).isNotNull();
        assertThatThrownBy(() -> signerAt(doctor, null)).isInstanceOf(ClinicalException.SignerIdentityIncomplete.class);
    }

    @Test
    void cannotSignRegularNotesOnceTheEncounterIsClosed() {
        Clinician doctor = doctor();
        NoteDraft draft = NoteDraft.start(encounter, doctor, progress(), null, null, POLICY, clockAt(NOW));

        assertThatThrownBy(() -> draft.sign(signerAt(doctor, NOW), closed(encounter), POLICY, clockAt(NOW)))
                .isInstanceOf(ClinicalException.EncounterClosed.class);
    }
}
