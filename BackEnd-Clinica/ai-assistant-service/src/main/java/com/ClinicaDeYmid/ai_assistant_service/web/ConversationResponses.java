package com.ClinicaDeYmid.ai_assistant_service.web;

import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ConversationResponses {

    private ConversationResponses() {
    }

    record ConversationView(UUID uuid, long version, String title, String status, Instant createdAt,
                            Instant updatedAt, Instant closedAt) {

        static ConversationView from(ConversationViews.ConversationSummary summary) {
            return new ConversationView(summary.uuid(), summary.version(), summary.title(), summary.status(),
                    summary.createdAt(), summary.updatedAt(), summary.closedAt());
        }
    }

    record MessageView(UUID uuid, int position, String role, String content, Instant createdAt) {

        static MessageView from(ConversationViews.MessageView message) {
            return new MessageView(message.uuid(), message.position(), message.role(), message.content(),
                    message.createdAt());
        }
    }

    record DetailView(ConversationView conversation, List<MessageView> messages) {

        static DetailView from(ConversationViews.ConversationDetail detail) {
            return new DetailView(ConversationView.from(detail.summary()),
                    detail.messages().stream().map(MessageView::from).toList());
        }
    }

    record ExchangeView(MessageView question, MessageView answer) {

        static ExchangeView from(ConversationViews.Exchange exchange) {
            return new ExchangeView(MessageView.from(exchange.question()), MessageView.from(exchange.answer()));
        }
    }

    record PageView(List<ConversationView> content, int page, int size, long totalElements) {

        static PageView from(ConversationViews.Page<ConversationViews.ConversationSummary> page) {
            return new PageView(page.content().stream().map(ConversationView::from).toList(), page.page(),
                    page.size(), page.totalElements());
        }
    }
}
