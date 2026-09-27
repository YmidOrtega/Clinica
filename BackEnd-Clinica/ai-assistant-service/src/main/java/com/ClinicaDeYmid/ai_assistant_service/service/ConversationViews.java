package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Conversation;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.ConversationMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConversationViews {

    private ConversationViews() {
    }

    public record ConversationSummary(UUID uuid, long version, String title, String status, Instant createdAt,
                                      Instant updatedAt, Instant closedAt) {

        static ConversationSummary of(Conversation conversation) {
            return new ConversationSummary(conversation.uuid(), conversation.version(), conversation.title(),
                    conversation.status().name(), conversation.createdAt(), conversation.updatedAt(),
                    conversation.closedAt());
        }
    }

    public record ConversationDetail(ConversationSummary summary, List<MessageView> messages) {

        static ConversationDetail of(Conversation conversation) {
            return new ConversationDetail(ConversationSummary.of(conversation),
                    conversation.messages().stream().map(MessageView::of).toList());
        }
    }

    public record MessageView(UUID uuid, int position, String role, String content, Instant createdAt) {

        static MessageView of(ConversationMessage message) {
            return new MessageView(message.uuid(), message.position(), message.role().name(), message.content(),
                    message.createdAt());
        }
    }

    public record Exchange(MessageView question, MessageView answer, List<UUID> proposedActions) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements) {
    }
}
