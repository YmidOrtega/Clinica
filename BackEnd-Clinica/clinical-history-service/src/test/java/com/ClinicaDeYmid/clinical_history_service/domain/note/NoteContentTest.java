package com.ClinicaDeYmid.clinical_history_service.domain.note;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;

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
    void diagnosesAreCie10CodesWithASinglePrincipal() {
        Diagnosis hypertension = new Diagnosis(" i10.x ", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.CONFIRMED_REPEATED, null, null);
        Diagnosis diabetes = new Diagnosis("E119", Diagnosis.Role.RELATED, Diagnosis.Type.CONFIRMED_NEW, null, null);

        assertThat(hypertension.code()).isEqualTo("I10X");
        assertThat(new NoteContent.Progress("s", "o", "a", "p", List.of(hypertension, diabetes)).missingFields()).isEmpty();
        assertThat(new NoteContent.Progress("s", "o", "a", "p", List.of(diabetes)).missingFields()).containsExactly("diagnoses.principal");
        assertThat(new NoteContent.Progress("s", "o", "a", "p", List.of()).missingFields()).isEmpty();
        assertThatThrownBy(() -> new NoteContent.Progress("s", "o", "a", "p", List.of(hypertension, hypertension)))
                .hasMessageContaining("repite el código I10X");
        assertThatThrownBy(() -> new NoteContent.Progress("s", "o", "a", "p",
                List.of(hypertension, new Diagnosis("E119", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.IMPRESSION, null, null))))
                .hasMessageContaining("un diagnóstico principal");
        assertThatThrownBy(() -> new Diagnosis("I1", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.IMPRESSION, null, null))
                .isInstanceOf(ClinicalException.InvalidData.class);
    }

    @Test
    void eachTypeDeclaresItsMandatoryFields() {
        assertThat(new NoteContent.Admission(null, null, null, null, null, List.of()).missingFields())
                .containsExactly("chiefComplaint", "currentIllness", "physicalExam", "assessment", "plan", "diagnoses.principal");
        assertThat(new NoteContent.Triage(null, null, null).missingFields()).containsExactly("level", "reason");
        assertThat(new NoteContent.Consultation(null, null, null, null, List.of()).missingFields())
                .containsExactly("specialty", "reason", "findings", "recommendations");
        assertThat(new NoteContent.Discharge(null, null, null, null, null, List.of()).missingFields())
                .containsExactly("admissionSummary", "evolutionSummary", "dischargeCondition", "recommendations", "followUp", "diagnoses.principal");
        assertThat(new NoteContent.Addendum(null, null).missingFields()).containsExactly("amendsNoteId", "text");
    }
}
