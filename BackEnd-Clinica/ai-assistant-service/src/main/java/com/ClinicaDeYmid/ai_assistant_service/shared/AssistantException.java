package com.ClinicaDeYmid.ai_assistant_service.shared;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class AssistantException extends DomainException {

    private AssistantException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends AssistantException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "ASSISTANT_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class ConversationNotFound extends AssistantException {
        public ConversationNotFound() {
            super(ErrorCategory.NOT_FOUND, "CONVERSATION_NOT_FOUND", "No se encontró la conversación solicitada");
        }
    }

    public static final class ModelUnavailable extends AssistantException {
        public ModelUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "ASSISTANT_MODEL_UNAVAILABLE",
                    "El modelo local no respondió a tiempo; intente de nuevo en un momento");
        }
    }

    public static final class ActionNotFound extends AssistantException {
        public ActionNotFound() {
            super(ErrorCategory.NOT_FOUND, "ACTION_NOT_FOUND", "No se encontró la acción propuesta");
        }
    }

    public static final class ActionNotPending extends AssistantException {
        public ActionNotPending(String status) {
            super(ErrorCategory.RULE_VIOLATION, "ACTION_NOT_PENDING",
                    "La acción ya no espera confirmación (" + status + ")");
        }
    }

    public static final class ActionExpired extends AssistantException {
        public ActionExpired() {
            super(ErrorCategory.RULE_VIOLATION, "ACTION_EXPIRED",
                    "La propuesta venció; pídale al asistente que la vuelva a proponer");
        }
    }

    public static final class ConversationClosed extends AssistantException {
        public ConversationClosed() {
            super(ErrorCategory.RULE_VIOLATION, "CONVERSATION_CLOSED", "La conversación ya está cerrada");
        }
    }
}
