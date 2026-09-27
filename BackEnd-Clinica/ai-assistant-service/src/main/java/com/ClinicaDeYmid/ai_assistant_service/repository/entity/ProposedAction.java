package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionStatus;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.ai_assistant_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposed_actions")
public class ProposedAction {

    public static final int OUTCOME_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "owner_uuid", nullable = false, updatable = false)
    private UUID ownerUuid;

    @Column(name = "invoice_uuid", nullable = false, updatable = false)
    private UUID invoiceUuid;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 24)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 30)
    private ActionKind kind;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ActionStatus status;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "outcome", length = OUTCOME_LENGTH)
    private String outcome;

    protected ProposedAction() {
    }

    public static ProposedAction propose(Conversation conversation, UUID invoiceUuid, String invoiceNumber,
                                         ActionKind kind, String reason, Instant at, Duration lifetime) {
        ProposedAction action = new ProposedAction();
        action.uuid = UUID.randomUUID();
        action.conversation = Rules.required(conversation, "conversation");
        action.ownerUuid = conversation.ownerUuid();
        action.invoiceUuid = Rules.required(invoiceUuid, "invoiceUuid");
        action.invoiceNumber = Rules.requiredText(invoiceNumber, "invoiceNumber", 24);
        action.kind = Rules.required(kind, "kind");
        action.reason = Rules.requiredText(reason, "reason", 500);
        action.status = ActionStatus.PROPOSED;
        action.proposedAt = at;
        action.expiresAt = at.plus(lifetime);
        return action;
    }

    public void startExecuting(Instant at) {
        requirePending(at);
        status = ActionStatus.EXECUTING;
        decidedAt = at;
    }

    public void succeeded(String result) {
        finish(ActionStatus.DONE, result);
    }

    public void failed(String result) {
        finish(ActionStatus.FAILED, result);
    }

    public void discard(Instant at) {
        if (status != ActionStatus.PROPOSED) {
            throw new AssistantException.ActionNotPending(status.name());
        }
        status = ActionStatus.DISCARDED;
        decidedAt = at;
    }

    private void requirePending(Instant at) {
        if (status != ActionStatus.PROPOSED) {
            throw new AssistantException.ActionNotPending(status.name());
        }
        if (!at.isBefore(expiresAt)) {
            throw new AssistantException.ActionExpired();
        }
    }

    private void finish(ActionStatus result, String text) {
        if (status != ActionStatus.EXECUTING) {
            throw new IllegalStateException("Only an executing action finishes");
        }
        status = result;
        outcome = text == null ? null : text.length() > OUTCOME_LENGTH ? text.substring(0, OUTCOME_LENGTH) : text;
    }

    public boolean ownedBy(UUID userUuid) {
        return ownerUuid.equals(userUuid);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public UUID conversationUuid() {
        return conversation.uuid();
    }

    public UUID invoiceUuid() {
        return invoiceUuid;
    }

    public String invoiceNumber() {
        return invoiceNumber;
    }

    public ActionKind kind() {
        return kind;
    }

    public String reason() {
        return reason;
    }

    public ActionStatus status() {
        return status;
    }

    public Instant proposedAt() {
        return proposedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String outcome() {
        return outcome;
    }
}
