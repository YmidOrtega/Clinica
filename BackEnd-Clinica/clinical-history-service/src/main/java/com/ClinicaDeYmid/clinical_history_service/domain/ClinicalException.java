package com.ClinicaDeYmid.clinical_history_service.domain;

import com.ClinicaDeYmid.clinical_history_service.domain.access.AccessAction;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

import java.util.List;
import java.util.UUID;

public sealed abstract class ClinicalException extends DomainException {

    private ClinicalException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends ClinicalException {
        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "CLINICAL_INVALID_DATA", "El campo '" + field + "' " + problem);
        }
    }

    public static final class ClinicalRoleRequired extends ClinicalException {
        public ClinicalRoleRequired() {
            super(ErrorCategory.FORBIDDEN, "CLINICAL_ROLE_REQUIRED", "Solo médicos y personal de enfermería pueden realizar esta acción");
        }
    }

    public static final class SignerIdentityIncomplete extends ClinicalException {
        public SignerIdentityIncomplete() {
            super(ErrorCategory.FORBIDDEN, "SIGNER_IDENTITY_INCOMPLETE",
                    "Tu sesión no trae los datos necesarios para firmar; inicia sesión de nuevo");
        }
    }

    public static final class RecentAuthenticationRequired extends ClinicalException {
        public RecentAuthenticationRequired() {
            super(ErrorCategory.FORBIDDEN, "RECENT_AUTHENTICATION_REQUIRED",
                    "Para firmar necesitas una sesión reciente; vuelve a autenticarte e intenta de nuevo");
        }
    }

    public static final class AccessDenied extends ClinicalException {

        private final Clinician actor;
        private final UUID patientUuid;
        private final AccessAction action;
        private final UUID resourceId;

        private AccessDenied(String code, String message, Clinician actor, UUID patientUuid, AccessAction action, UUID resourceId) {
            super(ErrorCategory.FORBIDDEN, code, message);
            this.actor = actor;
            this.patientUuid = patientUuid;
            this.action = action;
            this.resourceId = resourceId;
        }

        public static AccessDenied careRelationshipRequired(Clinician actor, UUID patientUuid, AccessAction action, UUID resourceId) {
            return new AccessDenied("CARE_RELATIONSHIP_REQUIRED",
                    "No tienes una relación de cuidado vigente con este paciente; si es una emergencia, solicita acceso de emergencia",
                    actor, patientUuid, action, resourceId);
        }

        public static AccessDenied restrictedNote(Clinician actor, UUID patientUuid, AccessAction action, UUID resourceId) {
            return new AccessDenied("RESTRICTED_NOTE",
                    "Esta nota está restringida al equipo de su atención; si es una emergencia, solicita acceso de emergencia",
                    actor, patientUuid, action, resourceId);
        }

        public Clinician actor() {
            return actor;
        }

        public UUID patientUuid() {
            return patientUuid;
        }

        public AccessAction action() {
            return action;
        }

        public UUID resourceId() {
            return resourceId;
        }
    }

    public static final class UnknownCode extends ClinicalException {
        public UnknownCode(String field, String code) {
            super(ErrorCategory.INVALID_INPUT, "UNKNOWN_CIE10_CODE",
                    "El código '" + code + "' del campo '" + field + "' no existe en el catálogo CIE-10 vigente");
        }
    }

    public static final class TerminologyUnavailable extends ClinicalException {
        public TerminologyUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "TERMINOLOGY_NOT_ACTIVE",
                    "No hay un catálogo CIE-10 activo; un administrador debe importarlo y activarlo");
        }
    }

    public static final class InvalidAttachment extends ClinicalException {
        public InvalidAttachment(String problem) {
            super(ErrorCategory.INVALID_INPUT, "INVALID_ATTACHMENT", "El anexo no es válido: " + problem);
        }
    }

    public static final class AttachmentNotFound extends ClinicalException {
        public AttachmentNotFound() {
            super(ErrorCategory.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "No se encontró el anexo");
        }
    }

    public static final class TooManyAttachments extends ClinicalException {
        public TooManyAttachments(int max) {
            super(ErrorCategory.RULE_VIOLATION, "TOO_MANY_ATTACHMENTS", "Una nota admite máximo " + max + " anexos");
        }
    }

    public static final class AttachmentStorageUnavailable extends ClinicalException {
        public AttachmentStorageUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "ATTACHMENT_STORAGE_UNAVAILABLE",
                    "El almacenamiento de anexos no está disponible; intenta de nuevo en unos minutos");
        }
    }

    public static final class RecordCopyNotFound extends ClinicalException {
        public RecordCopyNotFound() {
            super(ErrorCategory.NOT_FOUND, "RECORD_COPY_NOT_FOUND", "No se encontró la copia de la historia clínica");
        }
    }

    public static final class PatientNotFound extends ClinicalException {
        public PatientNotFound() {
            super(ErrorCategory.NOT_FOUND, "CLINICAL_PATIENT_NOT_FOUND", "No se encontró el paciente");
        }
    }

    public static final class PatientRegistryUnavailable extends ClinicalException {
        public PatientRegistryUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "PATIENT_REGISTRY_UNAVAILABLE",
                    "No es posible confirmar el paciente en este momento; intenta de nuevo en unos segundos");
        }
    }

    public static final class PatientNotAcceptingEncounters extends ClinicalException {
        public PatientNotAcceptingEncounters() {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_NOT_ACCEPTING_ENCOUNTERS",
                    "El paciente no admite atenciones nuevas por su estado actual");
        }
    }

    public static final class EncounterNotFound extends ClinicalException {
        public EncounterNotFound() {
            super(ErrorCategory.NOT_FOUND, "ENCOUNTER_NOT_FOUND", "No se encontró la atención clínica");
        }
    }

    public static final class EncounterClosed extends ClinicalException {
        public EncounterClosed() {
            super(ErrorCategory.RULE_VIOLATION, "ENCOUNTER_CLOSED",
                    "La atención está cerrada; solo admite notas aclaratorias");
        }
    }

    public static final class DraftTypeChange extends ClinicalException {
        public DraftTypeChange() {
            super(ErrorCategory.INVALID_INPUT, "DRAFT_TYPE_CHANGE", "Un borrador no puede cambiar de tipo ni de nota aclarada; descártalo y crea otro");
        }
    }

    public static final class EncounterNotReadyToClose extends ClinicalException {
        public EncounterNotReadyToClose(String problem) {
            super(ErrorCategory.RULE_VIOLATION, "ENCOUNTER_NOT_READY_TO_CLOSE", "La atención no se puede cerrar: " + problem);
        }
    }

    public static final class DraftNotFound extends ClinicalException {
        public DraftNotFound() {
            super(ErrorCategory.NOT_FOUND, "NOTE_DRAFT_NOT_FOUND", "No se encontró el borrador");
        }
    }

    public static final class DraftVersionMismatch extends ClinicalException {
        public DraftVersionMismatch() {
            super(ErrorCategory.PRECONDITION_FAILED, "VERSION_MISMATCH",
                    "El borrador fue modificado después de consultarlo; vuelve a consultarlo antes de continuar");
        }
    }

    public static final class NoteNotFound extends ClinicalException {
        public NoteNotFound() {
            super(ErrorCategory.NOT_FOUND, "NOTE_NOT_FOUND", "No se encontró la nota clínica");
        }
    }

    public static final class NoteTypeNotAllowed extends ClinicalException {
        public NoteTypeNotAllowed() {
            super(ErrorCategory.FORBIDDEN, "NOTE_TYPE_NOT_ALLOWED_FOR_ROLE", "Tu perfil no puede registrar este tipo de nota");
        }
    }

    public static final class NotTheAuthor extends ClinicalException {
        public NotTheAuthor() {
            super(ErrorCategory.FORBIDDEN, "NOT_THE_AUTHOR", "Solo el autor de la nota puede realizar esta acción");
        }
    }

    public static final class NoteIncomplete extends ClinicalException {

        private final List<String> missingFields;

        public NoteIncomplete(List<String> missingFields) {
            super(ErrorCategory.RULE_VIOLATION, "NOTE_INCOMPLETE",
                    "La nota no se puede firmar; faltan campos obligatorios: " + String.join(", ", missingFields));
            this.missingFields = List.copyOf(missingFields);
        }

        public List<String> missingFields() {
            return missingFields;
        }
    }

    public static final class InvalidOccurrence extends ClinicalException {
        public InvalidOccurrence(String problem) {
            super(ErrorCategory.INVALID_INPUT, "INVALID_OCCURRENCE_TIME", "La fecha de ocurrencia " + problem);
        }
    }

    public static final class NoteAlreadyVoided extends ClinicalException {
        public NoteAlreadyVoided() {
            super(ErrorCategory.CONFLICT, "NOTE_ALREADY_VOIDED", "La nota ya fue anulada");
        }
    }

    public static final class InvalidAmendment extends ClinicalException {
        public InvalidAmendment(String problem) {
            super(ErrorCategory.RULE_VIOLATION, "INVALID_AMENDMENT", "La nota aclaratoria no es válida: " + problem);
        }
    }
}
