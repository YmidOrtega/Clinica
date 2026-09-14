package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.OPENED_AT;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.clockAt;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.closed;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.openEncounter;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.signed;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.triage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedNoteTest {

    private final Clock clock = clockAt(OPENED_AT.plusSeconds(3600));
    private final Encounter encounter = openEncounter(EncounterType.EMERGENCY);
    private final Clinician triageNurse = nurse();
    private final SignedNote note = signed(encounter, triageNurse, triage());

    @Test
    void addendaComeFromTheSameProfileWithinTheSameEncounter() {
        note.requireAmendableBy(nurse(), encounter.id(), false);

        assertThatThrownBy(() -> note.requireAmendableBy(doctor(), encounter.id(), false))
                .isInstanceOf(ClinicalException.InvalidAmendment.class)
                .hasMessageContaining("perfil");
        assertThatThrownBy(() -> note.requireAmendableBy(nurse(), UUID.randomUUID(), false))
                .isInstanceOf(ClinicalException.InvalidAmendment.class)
                .hasMessageContaining("otra atención");
        assertThatThrownBy(() -> note.requireAmendableBy(nurse(), encounter.id(), true))
                .isInstanceOf(ClinicalException.InvalidAmendment.class)
                .hasMessageContaining("anulada");
    }

    @Test
    void onlyTheAuthorVoidsANoteAndOnlyOnce() {
        NoteVoid voiding = note.voidBy(triageNurse, encounter, "  Registrada en el paciente equivocado ", false, clock);

        assertThat(voiding.noteId()).isEqualTo(note.id());
        assertThat(voiding.reason()).isEqualTo("Registrada en el paciente equivocado");
        assertThat(voiding.voidedAt()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> note.voidBy(nurse(), encounter, "Error", false, clock))
                .isInstanceOf(ClinicalException.NotTheAuthor.class);
        assertThatThrownBy(() -> note.voidBy(triageNurse, encounter, "Error", true, clock))
                .isInstanceOf(ClinicalException.NoteAlreadyVoided.class);
        assertThatThrownBy(() -> note.voidBy(triageNurse, encounter, "  ", false, clock))
                .isInstanceOf(ClinicalException.InvalidData.class);
    }

    @Test
    void closedEncountersAreCorrectedWithAddendaInsteadOfVoiding() {
        assertThatThrownBy(() -> note.voidBy(triageNurse, closed(encounter), "Error", false, clock))
                .isInstanceOf(ClinicalException.EncounterClosed.class);
    }
}
