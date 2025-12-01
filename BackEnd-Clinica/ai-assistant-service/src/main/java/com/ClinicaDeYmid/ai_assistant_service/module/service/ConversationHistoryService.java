package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.MessageDto;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationHistory;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationMessage;
import com.ClinicaDeYmid.ai_assistant_service.module.repository.ConversationHistoryRepository;
import com.ClinicaDeYmid.ai_assistant_service.module.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationHistoryService {

    private final ConversationHistoryRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    /**
     * Obtiene o crea una conversación activa para el usuario
     */
    @Transactional
    public ConversationHistory getOrCreateActiveConversation(Long userId, String username, String sessionId) {
        log.debug("Getting or creating active conversation for user: {}", userId);

        // Si se proporciona sessionId, buscar por session
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ConversationHistory> existing = conversationRepository.findBySessionId(sessionId);
            if (existing.isPresent()) {
                log.debug("Found existing conversation with sessionId: {}", sessionId);
                return existing.get();
            }
        }

        // Buscar conversación activa del usuario
        Optional<ConversationHistory> activeConversation =
                conversationRepository.findByUserIdAndIsActiveTrue(userId);

        if (activeConversation.isPresent()) {
            log.debug("Found active conversation for user: {}", userId);
            return activeConversation.get();
        }

        // Crear nueva conversación
        ConversationHistory newConversation = ConversationHistory.builder()
                .userId(userId)
                .username(username)
                .sessionId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .isActive(true)
                .build();

        ConversationHistory saved = conversationRepository.save(newConversation);
        log.info("Created new conversation for user: {} with sessionId: {}", userId, saved.getSessionId());
        return saved;
    }

    /**
     * Guarda un mensaje en la conversación
     */
    @Transactional
    public void saveMessage(ConversationHistory conversation,
                            ConversationMessage.MessageRole role,
                            String content,
                            String metadata) {
        ConversationMessage message = ConversationMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .metadata(metadata)
                .build();

        messageRepository.save(message);
        log.debug("Saved {} message for conversation: {}", role, conversation.getSessionId());
    }

    /**
     * Obtiene el historial de mensajes de una conversación
     */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * Obtiene el historial completo de un usuario
     */
    @Transactional(readOnly = true)
    public List<ConversationHistoryDto> getUserConversationHistory(Long userId) {
        List<ConversationHistory> conversations =
                conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return conversations.stream()
                .map(this::toConversationHistoryDto)
                .collect(Collectors.toList());
    }

    /**
     * Cierra una conversación
     */
    @Transactional
    public void closeConversation(String sessionId) {
        Optional<ConversationHistory> conversation = conversationRepository.findBySessionId(sessionId);
        conversation.ifPresent(conv -> {
            conv.closeConversation();
            conversationRepository.save(conv);
            log.info("Closed conversation with sessionId: {}", sessionId);
        });
    }

    /**
     * Cierra todas las conversaciones activas de un usuario
     */
    @Transactional
    public void closeAllUserConversations(Long userId) {
        List<ConversationHistory> activeConversations =
                conversationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);

        activeConversations.forEach(conv -> {
            conv.closeConversation();
            conversationRepository.save(conv);
        });

        log.info("Closed {} active conversations for user: {}", activeConversations.size(), userId);
    }

    // Mapper privado
    private ConversationHistoryDto toConversationHistoryDto(ConversationHistory conversation) {
        List<MessageDto> messages = conversation.getMessages().stream()
                .map(msg -> new MessageDto(
                        msg.getId(),
                        msg.getRole().name(),
                        msg.getContent(),
                        msg.getCreatedAt(),
                        msg.getMetadata()
                ))
                .collect(Collectors.toList());

        return new ConversationHistoryDto(
                conversation.getId(),
                conversation.getSessionId(),
                conversation.getUsername(),
                conversation.getIsActive(),
                conversation.getCreatedAt(),
                conversation.getClosedAt(),
                messages
        );
    }
}