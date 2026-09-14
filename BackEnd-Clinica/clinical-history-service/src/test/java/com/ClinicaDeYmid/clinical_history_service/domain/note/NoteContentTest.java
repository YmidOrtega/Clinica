package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteContentTest {

    @Test
    void normalizesBlankTextToMissingAndTrimsTrailingSpacesPerLine() {
        NoteContent.Nursing nursing = new NoteContent.Nursing("  Paciente tranquilo   \n  sin dolor  \t", "   ");

        assertThat(nursing.observations()).isEqualTo("Paciente tranquilo\n  sin dolor");
        assertThat(nursing.careProvided()).isNull();
        assertThat(nursing.missingFields()).containsExactly("careProvided");
    }

    @Test
    void rejectsTextLongerThanAllowed() {
        assertThatThrownBy(() -> new NoteContent.Triage(TriageLevel.II, "x".repeat(501), null))
                .isInstanceOf(ClinicalException.InvalidData.class)
                .hasMessageContaining("reason");
    }

    @Test
    void eachTypeDeclaresItsMandatoryFields() {
        assertThat(new NoteContent.Admission(null, null, null, null, null).missingFields())
                .containsExactly("chiefComplaint", "currentIllness", "physicalExam", "assessment", "plan");
        assertThat(new NoteContent.Triage(null, null, null).missingFields()).containsExactly("level", "reason");
        assertThat(new NoteContent.Consultation(null, null, null, null).missingFields())
                .containsExactly("specialty", "reason", "findings", "recommendations");
        assertThat(new NoteContent.Discharge(null, null, null, null, null).missingFields())
                .containsExactly("admissionSummary", "evolutionSummary", "dischargeCondition", "recommendations", "followUp");
        assertThat(new NoteContent.Addendum(null, null).missingFields()).containsExactly("amendsNoteId", "text");
    }
}
