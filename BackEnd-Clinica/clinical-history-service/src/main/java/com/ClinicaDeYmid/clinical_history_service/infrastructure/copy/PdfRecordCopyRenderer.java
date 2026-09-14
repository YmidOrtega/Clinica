package com.ClinicaDeYmid.clinical_history_service.infrastructure.copy;

import com.ClinicaDeYmid.clinical_history_service.application.copy.RecordCopyContent;
import com.ClinicaDeYmid.clinical_history_service.application.copy.RecordCopyRenderer;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.EncounterRecord;
import com.ClinicaDeYmid.clinical_history_service.application.record.ClinicalRecordQueries.NoteEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.attachment.Attachment;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterStatus;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainLink;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.ChainVerification;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.EntryType;
import com.ClinicaDeYmid.clinical_history_service.domain.integrity.LedgerEntry;
import com.ClinicaDeYmid.clinical_history_service.domain.note.Diagnosis;
import com.ClinicaDeYmid.clinical_history_service.domain.note.NoteContent;
import com.ClinicaDeYmid.clinical_history_service.domain.note.SignedNote;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.update.AppliedUpdate;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemDetails;
import com.ClinicaDeYmid.clinical_history_service.domain.update.ListItemHistory;
import com.ClinicaDeYmid.clinical_history_service.domain.update.VitalSignObservation;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.copy.PdfPages.Style;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
class PdfRecordCopyRenderer implements RecordCopyRenderer {

    private final String institution;
    private final ZoneId zone;
    private final DateTimeFormatter dateTime;

    PdfRecordCopyRenderer(@Value("${clinica.clinical.institution-name:Clínica de Ymid}") String institution,
                          @Value("${clinica.clinical.time-zone:America/Bogota}") ZoneId zone) {
        this.institution = institution;
        this.zone = zone;
        this.dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
    }

    @Override
    public byte[] render(RecordCopyContent content) {
        try (PdfPages pdf = new PdfPages()) {
            header(pdf, content);
            patient(pdf, content);
            integrity(pdf, content);
            lists(pdf, content);
            vitalSigns(pdf, content);
            for (EncounterRecord record : content.encounters()) {
                encounter(pdf, content, record);
            }
            verification(pdf, content);
            return pdf.finish(institution + " · Copia " + content.copyId(), "Copia de la historia clínica " + content.copyId());
        }
    }

    private void header(PdfPages pdf, RecordCopyContent content) {
        pdf.line(Style.TITLE, "Copia de la historia clínica");
        pdf.line(Style.TEXT, institution);
        pdf.gap(6);
        pdf.field("Identificador de la copia", content.copyId().toString());
        pdf.field("Generada", format(content.generatedAt()) + " (" + zone + ")");
        pdf.field("Solicitada por", content.requestedBy() + " · " + content.requestedRole());
        pdf.field("Motivo", content.reason());
        pdf.field("Periodo", content.periodFrom() == null && content.periodTo() == null ? "Historia completa"
                : "Desde " + (content.periodFrom() == null ? "el inicio" : format(content.periodFrom()))
                + " hasta " + (content.periodTo() == null ? "la fecha" : format(content.periodTo())));
        pdf.gap(10);
    }

    private void patient(PdfPages pdf, RecordCopyContent content) {
        pdf.line(Style.SECTION, "Paciente");
        describe(pdf, content.patient());
        for (PatientReference linked : content.linkedRecords()) {
            pdf.gap(4);
            pdf.line(Style.SUBSECTION, "Registro provisional vinculado");
            describe(pdf, linked);
        }
        pdf.gap(10);
    }

    private void describe(PdfPages pdf, PatientReference patient) {
        switch (patient) {
            case PatientReference.Registered registered -> {
                pdf.field("Nombre", registered.firstNames() + " " + registered.lastNames());
                pdf.field("Documento", registered.document().type() + " " + registered.document().number());
                pdf.field("Fecha de nacimiento", registered.birthDate().toString());
                pdf.field("Sexo", registered.sex().name());
                pdf.field("Estado", registered.status().name() + (registered.dateOfDeath() == null ? "" : " (" + registered.dateOfDeath() + ")"));
                pdf.field("Régimen de salud", registered.healthRegime());
            }
            case PatientReference.Unidentified unidentified -> {
                pdf.field("Código provisional", unidentified.code());
                pdf.field("Sexo", unidentified.sex().name());
                pdf.field("Año de nacimiento estimado", String.valueOf(unidentified.estimatedBirthYear()));
                pdf.field("Estado", unidentified.status().name());
            }
        }
        pdf.field("Identificador", patient.uuid().toString());
    }

    private void integrity(PdfPages pdf, RecordCopyContent content) {
        pdf.line(Style.SECTION, "Integridad del registro al generar la copia");
        for (ChainVerification chain : content.chains()) {
            pdf.line(Style.TEXT, "Cadena " + chain.patientUuid() + ": " + chain.entries() + " registros sellados · "
                    + (chain.verified() ? "verificada sin problemas" : "CON PROBLEMAS: " + chain.problems().size()));
            if (chain.head() != null) {
                pdf.line(Style.MONO, "Último eslabón #" + chain.head().sequence() + " " + chain.head().entryHash());
            }
            chain.problems().forEach(problem -> pdf.line(Style.MONO, "  " + problem.kind() + " " + problem.entryType() + " " + problem.entryId()));
        }
        pdf.gap(10);
    }

    private void lists(PdfPages pdf, RecordCopyContent content) {
        if (content.listItems().isEmpty()) {
            return;
        }
        pdf.line(Style.SECTION, "Alergias, antecedentes y medicamentos");
        for (ListItemHistory item : content.listItems()) {
            pdf.line(Style.TEXT, item.category() + " · " + item.status() + " · " + details(item.details()));
            pdf.line(Style.MUTED, "Registrado " + format(item.addedBy().recordedAt()) + " en la nota " + item.addedBy().noteId()
                    + item.events().stream().skip(1).map(event -> "; " + event.status() + " el " + format(event.origin().recordedAt())
                    + (event.origin().voided() ? " (nota anulada)" : "")).collect(Collectors.joining()));
        }
        pdf.gap(10);
    }

    private void vitalSigns(PdfPages pdf, RecordCopyContent content) {
        if (content.vitalSigns().isEmpty()) {
            return;
        }
        pdf.line(Style.SECTION, "Signos vitales");
        for (VitalSignObservation observation : content.vitalSigns()) {
            pdf.line(Style.TEXT, format(observation.measuredAt()) + " · " + observation.kind() + ": " + observation.value().toPlainString() + " "
                    + observation.kind().unit() + (observation.origin().voided() ? " (nota anulada)" : ""));
        }
        pdf.gap(10);
    }

    private void encounter(PdfPages pdf, RecordCopyContent content, EncounterRecord record) {
        pdf.gap(6);
        pdf.line(Style.SECTION, "Atención " + record.encounter().type() + " · " + format(record.encounter().openedAt()));
        pdf.field("Abierta por", person(record.encounter().openedBy()));
        if (record.encounter().admissionId() != null) {
            pdf.field("Admisión", record.encounter().admissionId());
        }
        if (record.encounter().status() instanceof EncounterStatus.Closed closed) {
            pdf.field("Cerrada", format(closed.closedAt()) + " por " + person(closed.closedBy()));
        }
        seal(pdf, content, new LedgerEntry.Key(EntryType.ENCOUNTER_OPENED, record.encounter().id()));
        for (NoteEntry entry : record.notes()) {
            note(pdf, content, entry);
        }
    }

    private void note(PdfPages pdf, RecordCopyContent content, NoteEntry entry) {
        SignedNote note = entry.note();
        pdf.gap(6);
        pdf.line(Style.SUBSECTION, "Nota " + note.type() + " · firmada " + format(note.recordedAt()));
        pdf.line(Style.MUTED, "Autor: " + person(note.author()) + " · " + note.signerEmail() + " · ocurrió " + format(note.occurredAt())
                + (note.extemporaneous() ? " · REGISTRO EXTEMPORÁNEO" : "") + (note.isRestricted() ? " · RESTRINGIDA " + note.restriction() : ""));
        entry.voiding().ifPresent(voiding -> pdf.line(Style.LABEL, "ANULADA el " + format(voiding.voidedAt()) + " por " + person(voiding.voidedBy())
                + ": " + voiding.reason()));
        content(pdf, note.content());
        if (!note.content().diagnoses().isEmpty()) {
            pdf.line(Style.LABEL, "Diagnósticos");
            note.content().diagnoses().forEach(diagnosis -> pdf.line(Style.TEXT, diagnosis(diagnosis)));
        }
        if (!note.updates().isEmpty()) {
            pdf.line(Style.LABEL, "Actualizaciones registradas con la nota");
            note.updates().forEach(update -> pdf.line(Style.TEXT, update(update)));
        }
        if (!note.attachments().isEmpty()) {
            pdf.line(Style.LABEL, "Anexos (se entregan por separado y se verifican con su SHA-256)");
            for (Attachment attachment : note.attachments()) {
                pdf.line(Style.TEXT, attachment.fileName() + " · " + attachment.mediaType().mimeType() + " · " + attachment.size() + " bytes");
                pdf.line(Style.MONO, "SHA-256 " + attachment.sha256());
            }
        }
        seal(pdf, content, new LedgerEntry.Key(EntryType.NOTE_SIGNED, note.id()));
    }

    private void content(PdfPages pdf, NoteContent content) {
        switch (content) {
            case NoteContent.Admission admission -> {
                pdf.field("Motivo de consulta", admission.chiefComplaint());
                pdf.field("Enfermedad actual", admission.currentIllness());
                pdf.field("Examen físico", admission.physicalExam());
                pdf.field("Análisis", admission.assessment());
                pdf.field("Plan", admission.plan());
            }
            case NoteContent.Progress progress -> {
                pdf.field("Subjetivo", progress.subjective());
                pdf.field("Objetivo", progress.objective());
                pdf.field("Análisis", progress.assessment());
                pdf.field("Plan", progress.plan());
            }
            case NoteContent.Triage triage -> {
                pdf.field("Nivel de triage", triage.level() == null ? null : triage.level().name());
                pdf.field("Motivo", triage.reason());
                pdf.field("Observaciones", triage.observations());
            }
            case NoteContent.Consultation consultation -> {
                pdf.field("Especialidad", consultation.specialty());
                pdf.field("Motivo", consultation.reason());
                pdf.field("Hallazgos", consultation.findings());
                pdf.field("Recomendaciones", consultation.recommendations());
            }
            case NoteContent.Nursing nursing -> {
                pdf.field("Observaciones", nursing.observations());
                pdf.field("Cuidados brindados", nursing.careProvided());
            }
            case NoteContent.Discharge discharge -> {
                pdf.field("Resumen del ingreso", discharge.admissionSummary());
                pdf.field("Resumen de la evolución", discharge.evolutionSummary());
                pdf.field("Condición de egreso", discharge.dischargeCondition());
                pdf.field("Recomendaciones", discharge.recommendations());
                pdf.field("Seguimiento", discharge.followUp());
            }
            case NoteContent.Addendum addendum -> {
                pdf.field("Aclara la nota", String.valueOf(addendum.amendsNoteId()));
                pdf.field("Aclaración", addendum.text());
            }
        }
    }

    private void seal(PdfPages pdf, RecordCopyContent content, LedgerEntry.Key key) {
        ChainLink link = content.links().get(key);
        if (link == null) {
            pdf.line(Style.MONO, "Sin sello en la cadena");
            return;
        }
        pdf.line(Style.MONO, "Sello #" + link.sequence() + " · clave " + link.keyId() + " · contenido " + link.payloadHash());
        pdf.line(Style.MONO, "Eslabón " + link.entryHash());
    }

    private void verification(PdfPages pdf, RecordCopyContent content) {
        pdf.pageBreak();
        pdf.line(Style.TITLE, "Cómo verificar esta copia");
        pdf.field("1. Autenticidad del documento",
                "La institución registró el SHA-256 de este archivo PDF y lo selló con su clave institucional (" + content.sealAlgorithm()
                        + ", clave " + content.activeSealKeyId() + "). Para comprobar que el archivo no fue modificado, el área de archivo "
                        + "clínico puede enviarlo a POST /api/v1/clinical/record-copies/" + content.copyId() + "/verification.");
        pdf.field("2. Integridad de cada registro",
                "Cada atención, nota, anulación y cierre lleva el hash de su contenido y el de su eslabón en la cadena del paciente. "
                        + "Cada eslabón incluye el hash del anterior y está firmado con la clave institucional; alterar un registro rompe su "
                        + "hash, y reescribir los hashes invalida la firma.");
        pdf.field("3. Claves públicas",
                "Las claves públicas del sello institucional se publican en GET /api/v1/clinical/seal-keys, incluidas las retiradas, para "
                        + "verificar registros sellados antes de una rotación.");
        pdf.field("4. Alcance",
                "Esta copia incluye las notas restringidas del paciente y deja constancia de las notas anuladas y de los registros "
                        + "extemporáneos. Los anexos se entregan por separado y deben coincidir con el SHA-256 listado en su nota.");
    }

    private String details(ListItemDetails details) {
        return switch (details) {
            case ListItemDetails.Allergy allergy -> allergy.substance() + " (" + allergy.severity() + ")"
                    + (allergy.reaction() == null ? "" : ": " + allergy.reaction());
            case ListItemDetails.ChronicCondition condition -> condition.code() + " " + condition.display()
                    + (condition.notes() == null ? "" : ": " + condition.notes());
            case ListItemDetails.CurrentMedication medication -> medication.medication()
                    + joined(medication.dose(), medication.route(), medication.frequency());
            case ListItemDetails.FamilyHistory family -> family.relationship() + ": " + family.condition();
            case ListItemDetails.PastHistory past -> past.kind() + ": " + past.description() + (past.year() == null ? "" : " (" + past.year() + ")");
            case ListItemDetails.Vaccination vaccination -> vaccination.vaccine() + joined(vaccination.dose(),
                    vaccination.appliedOn() == null ? null : vaccination.appliedOn().toString());
        };
    }

    private String update(AppliedUpdate update) {
        return switch (update) {
            case AppliedUpdate.ListItemAdded added -> "Agregado a " + added.details().category() + ": " + details(added.details());
            case AppliedUpdate.ListItemStatusChanged changed -> changed.category() + " " + changed.itemId() + " pasa a " + changed.status()
                    + ": " + changed.reason();
            case AppliedUpdate.VitalSignObserved observed -> observed.kind() + " " + observed.value().toPlainString() + " "
                    + observed.kind().unit() + " (" + format(observed.measuredAt()) + ")";
        };
    }

    private static String diagnosis(Diagnosis diagnosis) {
        return diagnosis.code() + " " + diagnosis.display() + " · " + diagnosis.role() + " · " + diagnosis.type()
                + " · CIE-10 " + diagnosis.catalogVersion();
    }

    private static String joined(String... parts) {
        String text = Arrays.stream(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(", "));
        return text.isEmpty() ? "" : " · " + text;
    }

    private static String person(Clinician clinician) {
        return clinician.uuid() + " (" + clinician.role() + ")";
    }

    private String format(Instant instant) {
        return dateTime.format(instant);
    }
}
