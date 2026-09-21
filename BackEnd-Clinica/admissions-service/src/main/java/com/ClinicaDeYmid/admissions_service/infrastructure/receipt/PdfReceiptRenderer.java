package com.ClinicaDeYmid.admissions_service.infrastructure.receipt;

import com.ClinicaDeYmid.admissions_service.application.receipt.ReceiptContent;
import com.ClinicaDeYmid.admissions_service.application.receipt.ReceiptRenderer;
import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionStatus;
import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationStatus;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.commons.documents.PdfPages;
import com.ClinicaDeYmid.commons.documents.PdfPages.Style;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
class PdfReceiptRenderer implements ReceiptRenderer {

    private final String institution;
    private final DateTimeFormatter moment;

    PdfReceiptRenderer(@Value("${clinica.admissions.time-zone:America/Bogota}") ZoneId zone,
                       @Value("${clinica.admissions.institution:Clínica de Ymid}") String institution) {
        this.institution = institution;
        this.moment = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
    }

    @Override
    public byte[] render(ReceiptContent content) {
        Admission admission = content.admission();
        try (PdfPages pdf = new PdfPages()) {
            pdf.line(Style.TITLE, "Comprobante de admisión");
            pdf.line(Style.MUTED, institution);
            pdf.gap(8);

            pdf.line(Style.SECTION, "Episodio");
            pdf.field("Número", admission.number());
            pdf.field("Tipo", label(admission.kind().name()));
            pdf.field("Causa", admission.cause().name());
            pdf.field("Estado", status(admission));
            pdf.field("Admitido", moment.format(admission.lastPhase().startedAt()));
            pdf.gap(6);

            pdf.line(Style.SECTION, "Paciente");
            pdf.field("Identificación", identification(content.patient()));
            pdf.field("Nombre", content.patient().label());
            pdf.gap(6);

            pdf.line(Style.SECTION, "Atención");
            for (AdmissionPhase phase : admission.phases()) {
                pdf.field(label(phase.kind().name()) + " · " + phase.configurationService().name(),
                        moment.format(phase.startedAt())
                                + (phase.endedAt() == null ? " (en curso)" : " a " + moment.format(phase.endedAt())));
            }
            if (admission.occupiesABed()) {
                pdf.field("Cama", admission.bedUuid().toString());
            }
            if (admission.attending() != null) {
                pdf.field("Profesional responsable",
                        admission.attending().fullName() + " · " + admission.attending().registrationNumber());
            }
            if (admission.companion() != null) {
                pdf.field("Acompañante", admission.companion().fullName() + " · "
                        + admission.companion().phoneNumber());
            }
            pdf.gap(6);

            if (admission.coverage() != null) {
                pdf.line(Style.SECTION, "Cobertura");
                pdf.field("Estado", coverage(admission));
                if (admission.coverage().contractNumber() != null) {
                    pdf.field("Contrato", admission.coverage().contractNumber());
                }
                pdf.gap(6);
            }

            if (!content.authorizations().isEmpty()) {
                pdf.line(Style.SECTION, "Autorizaciones");
                for (Authorization authorization : content.authorizations()) {
                    pdf.field(authorization.number(), authorization.type().name()
                            + (authorization.status() instanceof AuthorizationStatus.Revoked ? " (revocada)" : ""));
                }
                pdf.gap(6);
            }

            if (admission.status() instanceof AdmissionStatus.Discharged discharged) {
                pdf.line(Style.SECTION, "Egreso");
                pdf.field("Tipo", discharge(discharged.discharge().code()));
                pdf.field("Momento", moment.format(discharged.at()));
                pdf.gap(6);
            }

            pdf.line(Style.SECTION, "Verificación");
            pdf.line(Style.TEXT, "Este comprobante lleva una huella SHA-256 sellada con la clave "
                    + content.keyId() + " de la institución.");
            pdf.line(Style.TEXT, "Para comprobar que un archivo es exactamente el emitido, envíe su número "
                    + "de episodio y su huella a la verificación pública de admisiones.");
            pdf.field("Emitido por", content.issuedByName());
            return pdf.finish(institution + " · " + admission.number(),
                    "Comprobante de admisión " + admission.number());
        }
    }

    private static String identification(PatientReference patient) {
        return patient instanceof PatientReference.Registered registered
                ? registered.document().type() + " " + registered.document().number()
                : patient.label();
    }

    private static String status(Admission admission) {
        return switch (admission.status().code()) {
            case REGISTERED -> "Registrado";
            case ACTIVE -> "En atención";
            case DISCHARGED -> "Egresado";
            case CANCELLED -> "Anulado";
        };
    }

    private static String coverage(Admission admission) {
        return switch (admission.coverage().status()) {
            case COVERED -> "Con cobertura verificada";
            case NOT_COVERED -> "Sin cobertura";
            case UNKNOWN -> "Pendiente de verificar";
        };
    }

    private static String discharge(Discharge.Code code) {
        return switch (code) {
            case MEDICAL -> "Alta médica";
            case VOLUNTARY -> "Alta voluntaria";
            case REFERRAL -> "Remisión";
            case ESCAPE -> "Fuga";
            case DEATH -> "Fallecimiento";
        };
    }

    private static String label(String kind) {
        return switch (kind) {
            case "EMERGENCY" -> "Urgencias";
            case "INPATIENT" -> "Hospitalización";
            case "OUTPATIENT" -> "Ambulatorio";
            default -> kind;
        };
    }
}
