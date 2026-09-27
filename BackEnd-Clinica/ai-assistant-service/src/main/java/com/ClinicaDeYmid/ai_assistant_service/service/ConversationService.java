package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.ConversationRepository;
import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Conversation;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews.ConversationDetail;
import com.ClinicaDeYmid.ai_assistant_service.service.ConversationViews.ConversationSummary;
import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversations;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations, Clock clock) {
        this.conversations = conversations;
        this.clock = clock;
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
