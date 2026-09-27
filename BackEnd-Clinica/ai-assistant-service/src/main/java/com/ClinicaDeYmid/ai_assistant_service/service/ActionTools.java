package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Component
class ActionTools {

    static final String OWNER = "ownerUuid";
    static final String CONVERSATION = "conversationUuid";
    static final String PROPOSED = "proposedActions";

    private final ActionService actions;

    ActionTools(ActionService actions) {
        this.actions = actions;
    }

    @Tool(description = "Propone registrar el radicado de una factura ante el pagador, con el número y la fecha que dio "
            + "el usuario. No la radica: el usuario la confirma. No inventes el número ni la fecha: pregúntalos.")
    String proposeFiling(@ToolParam(description = "Número de la factura") String invoiceNumber,
                         @ToolParam(description = "Número de radicado que dio el pagador") String filingNumber,
                         @ToolParam(description = "Fecha de radicación, AAAA-MM-DD") String filedOn,
                         @ToolParam(description = "Por qué se propone, en una frase") String reason,
                         ToolContext context) {
        return recorded(context, () -> actions.proposeFiling(owner(context), conversation(context), invoiceNumber,
                filingNumber, LocalDate.parse(filedOn.strip()), reason));
    }

    @Tool(description = "Prepara la respuesta a una devolución o glosa con los códigos RE del manual, una respuesta por "
            + "causal (position), para que el usuario la revise y la confirme con segundo factor. Aceptar valor emite "
            + "una nota crédito. No inventes códigos, valores ni el número de la respuesta: pregúntalos al usuario.")
    String proposeObjectionAnswer(@ToolParam(description = "Número de la factura") String invoiceNumber,
                                  @ToolParam(description = "Número con que el pagador comunicó la glosa, por ejemplo GL-778") String payerRecord,
                                  @ToolParam(description = "Número de la respuesta de la clínica") String responseRecord,
                                  @ToolParam(description = "Fecha de la respuesta, AAAA-MM-DD") String respondedOn,
                                  @ToolParam(description = "Una respuesta por causal: position, code (RE9xxx), acceptedAmount y detail") List<ActionService.ObjectionAnswer> answers,
                                  @ToolParam(description = "Por qué se propone, en una frase") String reason,
                                  ToolContext context) {
        return recorded(context, () -> actions.proposeObjectionAnswer(owner(context), conversation(context),
                invoiceNumber, payerRecord, responseRecord, LocalDate.parse(respondedOn.strip()), answers, reason));
    }

    @Tool(description = "Propone al usuario una acción técnica sobre una factura para que él la confirme; no la ejecuta. "
            + "Tipos: SIGN (firmar una factura emitida sin firma), SEND_TO_DIAN (enviar o reenviar a la DIAN), "
            + "VALIDATE_RIPS (enviar el RIPS al Ministerio para obtener el CUV). Úsala solo cuando el usuario quiera "
            + "resolver el problema y explícale que debe confirmarla.")
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
        return recorded(context, () -> actions.propose(owner(context), conversation(context), invoiceNumber, parsed,
                reason));
    }

    @SuppressWarnings("unchecked")
    private static String recorded(ToolContext context, Supplier<ActionViews.ActionView> proposal) {
        try {
            ActionViews.ActionView proposed = proposal.get();
            ((List<UUID>) context.getContext().get(PROPOSED)).add(proposed.uuid());
            return "{\"proposed\":\"" + proposed.uuid() + "\",\"status\":\"PROPOSED\",\"note\":\"el usuario debe "
                    + "confirmarla en la aplicación antes de " + proposed.expiresAt() + "\"}";
        } catch (AssistantException | DateTimeParseException refused) {
            return "{\"error\":\"" + String.valueOf(refused.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private static UUID owner(ToolContext context) {
        return (UUID) context.getContext().get(OWNER);
    }

    private static UUID conversation(ToolContext context) {
        return (UUID) context.getContext().get(CONVERSATION);
    }
}
