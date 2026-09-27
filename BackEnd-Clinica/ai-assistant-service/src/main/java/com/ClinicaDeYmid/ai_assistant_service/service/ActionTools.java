package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
class ActionTools {

    static final String OWNER = "ownerUuid";
    static final String CONVERSATION = "conversationUuid";
    static final String PROPOSED = "proposedActions";

    private final ActionService actions;

    ActionTools(ActionService actions) {
        this.actions = actions;
    }

    @Tool(description = "Propone al usuario una acción técnica sobre una factura para que él la confirme; no la ejecuta. "
            + "Tipos: SIGN (firmar una factura emitida sin firma), SEND_TO_DIAN (enviar o reenviar a la DIAN), "
            + "VALIDATE_RIPS (enviar el RIPS al Ministerio para obtener el CUV). Úsala solo cuando el usuario quiera "
            + "resolver el problema y explícale que debe confirmarla.")
    @SuppressWarnings("unchecked")
    String proposeAction(@ToolParam(description = "SIGN, SEND_TO_DIAN o VALIDATE_RIPS") String kind,
                         @ToolParam(description = "Número de la factura") String invoiceNumber,
                         @ToolParam(description = "Por qué se propone, en una frase para el usuario") String reason,
                         ToolContext context) {
        ActionKind parsed;
        try {
            parsed = ActionKind.valueOf(kind.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException unknown) {
            return "{\"error\":\"tipo de acción desconocido: " + kind + "\"}";
        }
        try {
            ActionViews.ActionView proposed = actions.propose((UUID) context.getContext().get(OWNER),
                    (UUID) context.getContext().get(CONVERSATION), invoiceNumber, parsed, reason);
            ((List<UUID>) context.getContext().get(PROPOSED)).add(proposed.uuid());
            return "{\"proposed\":\"" + proposed.uuid() + "\",\"status\":\"PROPOSED\",\"note\":\"el usuario debe "
                    + "confirmarla en la aplicación antes de " + proposed.expiresAt() + "\"}";
        } catch (AssistantException refused) {
            return "{\"error\":\"" + refused.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
