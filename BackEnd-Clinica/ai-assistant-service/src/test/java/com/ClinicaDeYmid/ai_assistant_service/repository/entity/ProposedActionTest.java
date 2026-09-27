package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import com.ClinicaDeYmid.ai_assistant_service.shared.ActionKind;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionStatus;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProposedActionTest {

    private static final Instant NOW = Instant.parse("2026-09-27T15:00:00Z");

    @Test
    void anExpiredProposalIsNeverExecuted() {
        ProposedAction action = proposal();

        assertThatThrownBy(() -> action.startExecuting(NOW.plus(Duration.ofMinutes(30))))
                .isInstanceOf(AssistantException.ActionExpired.class);
        assertThat(action.status()).isEqualTo(ActionStatus.PROPOSED);
    }

    @Test
    void aProposalRunsOnceAndRecordsHowItEnded() {
        ProposedAction action = proposal();
        assertThatThrownBy(() -> action.succeeded("x")).isInstanceOf(IllegalStateException.class);

        action.startExecuting(NOW.plus(Duration.ofMinutes(5)));
        action.failed("x".repeat(ProposedAction.OUTCOME_LENGTH + 50));

        assertThat(action.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(action.outcome()).hasSize(ProposedAction.OUTCOME_LENGTH);
        assertThatThrownBy(() -> action.startExecuting(NOW.plus(Duration.ofMinutes(6))))
                .isInstanceOf(AssistantException.ActionNotPending.class);
        assertThatThrownBy(() -> action.discard(NOW)).isInstanceOf(AssistantException.ActionNotPending.class);
    }

    private static ProposedAction proposal() {
        Conversation conversation = Conversation.start(UUID.randomUUID(), "Revisión", Clock.fixed(NOW, ZoneOffset.UTC));
        return ProposedAction.propose(conversation, UUID.randomUUID(), "SETP1", ActionKind.SEND_TO_DIAN, "Reenviar",
                NOW, Duration.ofMinutes(30));
    }
}
