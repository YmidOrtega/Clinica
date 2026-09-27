package com.ClinicaDeYmid.ai_assistant_service.service;

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
import com.ClinicaDeYmid.commons.web.EntityTags;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

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
        return transactions.execute(status -> {
            Conversation conversation = conversations.findByUuidAndOwnerUuid(conversationUuid, ownerUuid)
                    .orElseThrow(AssistantException.ConversationNotFound::new);
            InvoiceSnapshot invoice = invoices.findByNumber(invoiceNumber.strip().toUpperCase(Locale.ROOT))
                    .filter(found -> "ISSUED".equals(found.status()))
                    .orElseThrow(() -> new AssistantException.InvalidData("invoiceNumber",
                            "no corresponde a una factura emitida vigente: " + invoiceNumber));
            ProposedAction proposed = actions.saveAndFlush(ProposedAction.propose(conversation, invoice.invoiceUuid(),
                    invoice.number(), kind, reason, Instant.now(clock), lifetime));
            log.info("Action {} proposed on invoice {}", kind, invoice.number());
            return ActionView.of(proposed);
        });
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
        BillingLookup result = billing.perform(started.kind(), started.invoiceUuid());
        return transactions.execute(status -> {
            ProposedAction action = owned(ownerUuid, uuid);
            switch (result) {
                case BillingLookup.Found found -> action.succeeded(summary(action.kind(), found.json()));
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
