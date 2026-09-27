package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.ConversationRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Conversation;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.ConversationMessage;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews.ConversationDetail;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews.ConversationSummary;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    static final int HISTORY_TURNS = 12;
    static final int QUESTION_LENGTH = 4000;

    private final ConversationRepository conversations;
    private final AssistantModel model;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, AssistantModel model,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.conversations = conversations;
        this.model = model;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public ConversationViews.Exchange ask(UUID ownerUuid, String userName, UUID uuid, String question) {
        if (question == null || question.isBlank()) {
            throw new AssistantException.InvalidData("content", "es obligatorio");
        }
        String asked = question.strip();
        if (asked.length() > QUESTION_LENGTH) {
            throw new AssistantException.InvalidData("content", "no puede superar " + QUESTION_LENGTH + " caracteres");
        }
        List<AssistantModel.Turn> history = transactions.execute(status -> {
            Conversation conversation = owned(ownerUuid, uuid);
            if (conversation.status() != Conversation.Status.OPEN) {
                throw new AssistantException.ConversationClosed();
            }
            List<ConversationMessage> messages = conversation.messages();
            return messages.subList(Math.max(0, messages.size() - HISTORY_TURNS), messages.size()).stream()
                    .map(message -> new AssistantModel.Turn(message.role() == ConversationMessage.Role.USER,
                            message.content()))
                    .toList();
        });
        List<UUID> proposed = Collections.synchronizedList(new ArrayList<>());
        String answer = model.answer(ownerUuid, uuid, userName, history, asked, proposed);
        return transactions.execute(status -> {
            Conversation conversation = owned(ownerUuid, uuid);
            ConversationMessage asked_ = conversation.append(ConversationMessage.Role.USER, asked, clock);
            ConversationMessage reply = conversation.append(ConversationMessage.Role.ASSISTANT,
                    answer.length() > ConversationMessage.CONTENT_LENGTH
                            ? answer.substring(0, ConversationMessage.CONTENT_LENGTH) : answer, clock);
            conversations.saveAndFlush(conversation);
            log.info("Conversation {} answered ({} characters)", conversation.uuid(), answer.length());
            return new ConversationViews.Exchange(ConversationViews.MessageView.of(asked_),
                    ConversationViews.MessageView.of(reply), List.copyOf(proposed));
        });
    }

    @Transactional
    public ConversationSummary start(UUID ownerUuid, String title) {
        Conversation started = conversations.saveAndFlush(Conversation.start(ownerUuid, title, clock));
        log.info("Conversation {} started", started.uuid());
        return ConversationSummary.of(started);
    }

    @Transactional(readOnly = true)
    public ConversationViews.Page<ConversationSummary> mine(UUID ownerUuid, int page, int size) {
        var found = conversations.findByOwnerUuidOrderByUpdatedAtDesc(ownerUuid, PageRequest.of(page, size));
        return new ConversationViews.Page<>(found.map(ConversationSummary::of).getContent(), page, size,
                found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID ownerUuid, UUID uuid) {
        return ConversationDetail.of(owned(ownerUuid, uuid));
    }

    @Transactional
    public ConversationSummary close(UUID ownerUuid, UUID uuid, long expectedVersion) {
        Conversation conversation = owned(ownerUuid, uuid);
        if (conversation.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        conversation.close(clock);
        Conversation closed = conversations.saveAndFlush(conversation);
        log.info("Conversation {} closed", closed.uuid());
        return ConversationSummary.of(closed);
    }

    private Conversation owned(UUID ownerUuid, UUID uuid) {
        return conversations.findByUuidAndOwnerUuid(uuid, ownerUuid)
                .orElseThrow(AssistantException.ConversationNotFound::new);
    }
}
