package com.ClinicaDeYmid.clinical_history_service.infrastructure.copy;

import com.ClinicaDeYmid.clinical_history_service.application.copy.RecordCopyContent;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.EncounterRecord;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.NoteEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteRestriction;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteVoid;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.doctor;
import static com.ClinicaDeYmid.clinical_history_service.domain.ClinicalFixtures.nurse;
import static org.assertj.core.api.Assertions.assertThat;

class PdfRecordCopyRendererTest {

    private static final Instant AT = Instant.parse("2026-09-14T15:00:00Z");

    @Test
    void rendersTheWholeRecordWithSealsAndAVerificationPage() throws Exception {
        UUID patientUuid = UUID.randomUUID();
        PatientReference patient = new PatientReference.Registered(patientUuid, 3, new PatientReference.Document("CEDULA_DE_CIUDADANIA", "1098765432"),
                "María José", "Núñez Peña", LocalDate.of(1985, 3, 2), PatientReference.Sex.FEMALE, PatientReference.Registered.Status.ACTIVE, null,
                "SUBSIDIZED", null);
        Encounter encounter = new Encounter(UUID.randomUUID(), patientUuid, EncounterType.EMERGENCY, null, AT, nurse(), new EncounterStatus.Open());
        SignedNote consultation = new SignedNote(UUID.randomUUID(), encounter.id(), doctor(), "doctor@clinica.test",
                new NoteContent.Consultation("Psiquiatría", "Ideación suicida", "Riesgo alto → vigilancia 1:1 😟", "Hospitalizar",
                        List.of(new Diagnosis("F322", Diagnosis.Role.PRINCIPAL, Diagnosis.Type.IMPRESSION, "Episodio depresivo grave", "2021-02-08"))),
                NoteRestriction.MENTAL_HEALTH, List.of(), List.of(), AT, AT.plusSeconds(3600 * 30), true);
        SignedNote nursing = new SignedNote(UUID.randomUUID(), encounter.id(), nurse(), "nurse@clinica.test",
                new NoteContent.Nursing("Tensión arterial elevada", "Reposo"), null, List.of(), List.of(), AT, AT.plusSeconds(60), false);
        RecordCopyContent content = new RecordCopyContent(UUID.randomUUID(), patient, List.of(), AT.plusSeconds(90000), UUID.randomUUID(),
                "MEDICAL_RECORDS", "Solicitud escrita de la paciente para trámite de EPS", null, null,
                List.of(new EncounterRecord(encounter, List.of(new NoteEntry(consultation, Optional.empty()),
                        new NoteEntry(nursing, Optional.of(new NoteVoid(nursing.id(), "Registrada en la paciente equivocada", nurse(), AT.plusSeconds(120))))))),
                List.of(), List.of(), List.of(ChainVerification.of(patientUuid, List.of(), List.of(), null)), Map.of(), "SHA256withECDSA", "seal-2026");

        byte[] pdf = new PdfRecordCopyRenderer("Clínica de Ymid", ZoneId.of("America/Bogota")).render(content);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Copia de la historia clínica", content.copyId().toString(), "María José Núñez Peña", "CEDULA_DE_CIUDADANIA 1098765432",
                    "Ideación suicida", "Riesgo alto ? vigilancia 1:1 ?", "F322 Episodio depresivo grave", "RESTRINGIDA MENTAL_HEALTH",
                    "REGISTRO EXTEMPORÁNEO", "ANULADA", "Registrada en la paciente equivocada", "Sin sello en la cadena",
                    "Cómo verificar esta copia", "/record-copies/" + content.copyId() + "/verification", "Página 1 de " + document.getNumberOfPages());
            assertThat(document.getDocumentInformation().getTitle()).contains(content.copyId().toString());
        }
    }
}
