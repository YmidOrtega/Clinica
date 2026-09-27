package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.ProposedAction;

import java.time.Instant;
import java.util.UUID;

public final class ActionViews {

    private ActionViews() {
    }

    public record ActionView(UUID uuid, long version, UUID conversationUuid, String invoiceNumber, String kind,
                             String label, String reason, String status, Instant proposedAt, Instant expiresAt,
                             Instant decidedAt, String outcome) {

        static ActionView of(ProposedAction action) {
            return new ActionView(action.uuid(), action.version(), action.conversationUuid(), action.invoiceNumber(),
                    action.kind().name(), action.kind().label(), action.reason(), action.status().name(),
                    action.proposedAt(), action.expiresAt(), action.decidedAt(), action.outcome());
        }
    }
}
