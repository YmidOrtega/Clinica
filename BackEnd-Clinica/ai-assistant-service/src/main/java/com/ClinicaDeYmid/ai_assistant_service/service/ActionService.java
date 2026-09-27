package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.client.BillingAction;
import com.ClinicaDeYmid.ai_assistant_service.client.BillingDirectory;
import com.ClinicaDeYmid.ai_assistant_service.client.BillingLookup;
import com.ClinicaDeYmid.ai_assistant_service.repository.ConversationRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.InvoiceSnapshotRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.ProposedActionRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Conversation;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.InvoiceSnapshot;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.ProposedAction;
import com.ClinicaDeYmid.ai_assistant_service.service.ActionViews.ActionView;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionStatus;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.ai_assistant_service.shared.Rules;
import com.ClinicaDeYmid.commons.web.EntityTags;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    private static final Pattern RESPONSE_CODE = Pattern.compile("^RE[0-9]{4}$");

    private final ProposedActionRepository actions;
    private final ConversationRepository conversations;
    private final InvoiceSnapshotRepository invoices;
    private final BillingDirectory billing;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Duration lifetime;

    public ActionService(ProposedActionRepository actions, ConversationRepository conversations,
                         InvoiceSnapshotRepository invoices, BillingDirectory billing,
                         PlatformTransactionManager transactionManager, Clock clock,
                         @Value("${clinica.assistant.actions.lifetime:PT30M}") Duration lifetime) {
        this.actions = actions;
        this.conversations = conversations;
        this.invoices = invoices;
        this.billing = billing;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public ActionView propose(UUID ownerUuid, UUID conversationUuid, String invoiceNumber, ActionKind kind,
                              String reason) {
        if (kind == ActionKind.FILE || kind == ActionKind.ANSWER_OBJECTION) {
            throw new AssistantException.InvalidData("kind", kind + " se propone con sus datos");
        }
        return store(ownerUuid, conversationUuid, invoiceNumber, kind, reason, null, null, null);
    }

    public ActionView proposeFiling(UUID ownerUuid, UUID conversationUuid, String invoiceNumber, String filingNumber,
                                    LocalDate filedOn, String reason) {
        String number = Rules.requiredText(filingNumber, "filingNumber", 60);
        Rules.required(filedOn, "filedOn");
        if (filedOn.isAfter(LocalDate.now(clock))) {
            throw new AssistantException.InvalidData("filedOn", "no puede ser una fecha futura");
        }
        ObjectNode registration = JSON.createObjectNode().put("filingNumber", number).put("filedOn", filedOn.toString());
        return store(ownerUuid, conversationUuid, invoiceNumber, ActionKind.FILE, reason, registration.toString(), null,
                null);
    }

    public ActionView proposeObjectionAnswer(UUID ownerUuid, UUID conversationUuid, String invoiceNumber,
                                             String payerRecord, String responseRecord, LocalDate respondedOn,
                                             List<ObjectionAnswer> answers, String reason) {
        String record = Rules.requiredText(responseRecord, "responseRecord", 60);
        Rules.required(respondedOn, "respondedOn");
        if (answers == null || answers.isEmpty() || answers.size() > 500) {
            throw new AssistantException.InvalidData("answers", "debe tener de 1 a 500 respuestas, una por causal");
        }
        ArrayNode items = JSON.createArrayNode();
        for (ObjectionAnswer answer : answers) {
            String code = Rules.requiredText(answer.code(), "answers.code", 6).toUpperCase(Locale.ROOT);
            if (!RESPONSE_CODE.matcher(code).matches()) {
                throw new AssistantException.InvalidData("answers.code", "debe ser un código RE del manual: " + code);
            }
            ObjectNode item = items.addObject().put("position", answer.position()).put("code", code);
            if (answer.acceptedAmount() != null) {
                item.put("acceptedAmount", answer.acceptedAmount());
            }
            if (answer.detail() != null && !answer.detail().isBlank()) {
                item.put("detail", Rules.requiredText(answer.detail(), "answers.detail", 1000));
            }
        }
        InvoiceSnapshot invoice = transactions.execute(status -> issued(invoiceNumber));
        UUID objection = awaitingObjection(invoice, Rules.requiredText(payerRecord, "payerRecord", 60));
        long version = billing.objectionVersion(objection).orElseThrow(() -> new AssistantException.InvalidData(
                "payerRecord", "no se pudo leer la glosa en billing; intente de nuevo"));
        ObjectNode response = JSON.createObjectNode().put("responseRecord", record)
                .put("respondedOn", respondedOn.toString());
        response.set("answers", items);
        return store(ownerUuid, conversationUuid, invoiceNumber, ActionKind.ANSWER_OBJECTION, reason,
                response.toString(), objection, version);
    }

    public record ObjectionAnswer(int position, String code, BigDecimal acceptedAmount, String detail) {
    }

    public boolean needsRecentSecondFactor(UUID ownerUuid, UUID uuid) {
        return transactions.execute(status -> owned(ownerUuid, uuid).kind().needsRecentSecondFactor());
    }

    private ActionView store(UUID ownerUuid, UUID conversationUuid, String invoiceNumber, ActionKind kind,
                             String reason, String payload, UUID target, Long targetVersion) {
        return transactions.execute(status -> {
            Conversation conversation = conversations.findByUuidAndOwnerUuid(conversationUuid, ownerUuid)
                    .orElseThrow(AssistantException.ConversationNotFound::new);
            InvoiceSnapshot invoice = issued(invoiceNumber);
            ProposedAction proposed = actions.saveAndFlush(ProposedAction.propose(conversation, invoice.invoiceUuid(),
                    invoice.number(), kind, reason, payload, target, targetVersion, Instant.now(clock), lifetime));
            log.info("Action {} proposed on invoice {}", kind, invoice.number());
            return ActionView.of(proposed);
        });
    }

    private InvoiceSnapshot issued(String invoiceNumber) {
        return invoices.findByNumber(Rules.requiredText(invoiceNumber, "invoiceNumber", 24).toUpperCase(Locale.ROOT))
                .filter(found -> "ISSUED".equals(found.status()))
                .orElseThrow(() -> new AssistantException.InvalidData("invoiceNumber",
                        "no corresponde a una factura emitida vigente: " + invoiceNumber));
    }

    private static UUID awaitingObjection(InvoiceSnapshot invoice, String payerRecord) {
        for (JsonNode objection : read(invoice.state()).path("objections")) {
            if (payerRecord.equalsIgnoreCase(objection.path("payerRecord").asText())
                    && "AWAITING_RESPONSE".equals(objection.path("status").asText())) {
                return UUID.fromString(objection.path("uuid").asText());
            }
        }
        throw new AssistantException.InvalidData("payerRecord", "la factura " + invoice.number()
                + " no tiene una devolución o glosa " + payerRecord + " esperando respuesta");
    }

    public List<ActionView> mine(UUID ownerUuid, ActionStatus status) {
        return transactions.execute(tx -> (status == null
                ? actions.findTop50ByOwnerUuidOrderByProposedAtDesc(ownerUuid)
                : actions.findTop50ByOwnerUuidAndStatusOrderByProposedAtDesc(ownerUuid, status))
                .stream().map(ActionView::of).toList());
    }

    public List<ActionView> ofUuids(UUID ownerUuid, List<UUID> uuids) {
        return transactions.execute(tx -> uuids.stream()
                .map(uuid -> actions.findByUuidAndOwnerUuid(uuid, ownerUuid))
                .flatMap(java.util.Optional::stream).map(ActionView::of).toList());
    }

    public ActionView confirm(UUID ownerUuid, UUID uuid, long expectedVersion) {
        ProposedAction started = transactions.execute(status -> {
            ProposedAction action = owned(ownerUuid, uuid);
            if (action.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            action.startExecuting(Instant.now(clock));
            return actions.saveAndFlush(action);
        });
        log.info("Action {} on invoice {} confirmed; asking billing", started.kind(), started.invoiceNumber());
        BillingLookup result = billing.perform(new BillingAction(started.kind(), started.invoiceUuid(),
                started.payload(), started.targetUuid(), started.targetVersion()));
        return transactions.execute(status -> {
            ProposedAction action = owned(ownerUuid, uuid);
            switch (result) {
                case BillingLookup.Found found -> action.succeeded(summary(action.kind(), found.json()));
                case BillingLookup.Refused refused when refused.status() == 401 ->
                        action.failed("billing pidió un segundo factor reciente; vuelva a autenticarse y proponga de nuevo");
                case BillingLookup.Refused refused when refused.status() == 412 ->
                        action.failed("la glosa cambió desde que se preparó la respuesta; pida una propuesta nueva");
                case BillingLookup.Refused refused -> action.failed("billing la rechazó (" + refused.status() + "): "
                        + problem(refused.problem()));
                case BillingLookup.Forbidden ignored -> action.failed("el usuario no tiene permiso en billing para hacerlo");
                case BillingLookup.NotFound ignored -> action.failed("billing no encontró la factura");
                case BillingLookup.Unavailable ignored ->
                        action.failed("billing no respondió; revise el estado de la factura antes de reintentar");
            }
            ProposedAction saved = actions.saveAndFlush(action);
            log.info("Action {} on invoice {} ended {}", saved.kind(), saved.invoiceNumber(), saved.status());
            return ActionView.of(saved);
        });
    }

    public ActionView discard(UUID ownerUuid, UUID uuid, long expectedVersion) {
        return transactions.execute(status -> {
            ProposedAction action = owned(ownerUuid, uuid);
            if (action.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            action.discard(Instant.now(clock));
            return ActionView.of(actions.saveAndFlush(action));
        });
    }

    private ProposedAction owned(UUID ownerUuid, UUID uuid) {
        return actions.findByUuidAndOwnerUuid(uuid, ownerUuid).orElseThrow(AssistantException.ActionNotFound::new);
    }

    private static String summary(ActionKind kind, String json) {
        JsonNode body = read(json);
        return switch (kind) {
            case SIGN -> "Firmada" + (body.path("dian").hasNonNull("status")
                    ? "; estado DIAN " + body.path("dian").path("status").asText() : "");
            case SEND_TO_DIAN -> "Enviada a la DIAN; estado " + body.path("dian").path("status").asText("sin respuesta aún");
            case VALIDATE_RIPS -> "Envío al Ministerio en estado " + body.path("status").asText("desconocido")
                    + (body.hasNonNull("cuv") ? " con CUV" : "");
            case FILE -> "Radicada con el número " + body.path("filingNumber").asText()
                    + (body.path("late").asBoolean(false) ? " (fuera del plazo)" : "");
            case ANSWER_OBJECTION -> "Respondida; valor aceptado " + (body.hasNonNull("acceptedAmount")
                    ? body.get("acceptedAmount").decimalValue().toPlainString() : "0")
                    + (body.hasNonNull("creditNoteNumber") ? " con la nota crédito " + body.get("creditNoteNumber").asText() : "");
        };
    }

    private static String problem(String body) {
        JsonNode problem = read(body);
        if (problem.hasNonNull("detail")) {
            return problem.path("code").asText("") + " " + problem.get("detail").asText();
        }
        return "sin detalle";
    }

    private static JsonNode read(String json) {
        try {
            return json == null || json.isBlank() ? JSON.createObjectNode() : JSON.readTree(json);
        } catch (Exception unreadable) {
            return JSON.createObjectNode();
        }
    }
}
