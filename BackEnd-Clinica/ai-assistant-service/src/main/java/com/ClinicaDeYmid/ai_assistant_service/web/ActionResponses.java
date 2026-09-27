package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.ai_assistant_service.service.ActionViews;

import java.time.Instant;
import java.util.UUID;

final class ActionResponses {

    private ActionResponses() {
    }

    record ActionView(UUID uuid, long version, UUID conversationUuid, String invoiceNumber, String kind, String label,
                      String reason, String status, Instant proposedAt, Instant expiresAt, Instant decidedAt,
                      String outcome) {

        static ActionView from(ActionViews.ActionView action) {
            return new ActionView(action.uuid(), action.version(), action.conversationUuid(), action.invoiceNumber(),
                    action.kind(), action.label(), action.reason(), action.status(), action.proposedAt(),
                    action.expiresAt(), action.decidedAt(), action.outcome());
        }
    }
}
