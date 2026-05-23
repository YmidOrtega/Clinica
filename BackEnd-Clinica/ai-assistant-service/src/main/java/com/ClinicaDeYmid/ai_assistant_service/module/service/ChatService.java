package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.infra.security.CustomUserDetails;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.AttentionExtractionResult;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.HealthProviderRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationHistory;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final AIService aiService;
    private final ConversationHistoryService conversationHistoryService;
    private final AdmissionsIntegrationService admissionsIntegrationService;
    private final AttentionDataExtractor attentionDataExtractor;
    private final ObjectMapper objectMapper;

    /**
     * Procesa un mensaje del usuario y genera respuesta
     */
    @Transactional
    public ChatResponseDto processMessage(ChatRequestDto request) {
        // Obtener información del usuario autenticado desde SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("User context not available for chat request");
            throw new RuntimeException("User authentication required");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String uuid = userDetails.getUuid();
        String username = userDetails.getEmail();

        // Fallback: generar userId a partir del UUID si no está disponible
        Long userId = userDetails.getUserId();
        if (userId == null) {
            userId = generateUserIdFromUuid(uuid);
            log.debug("Generated userId {} from UUID {} for user {}", userId, uuid, username);
        }

        log.info("Processing chat message for user: {} (ID: {}, UUID: {})", username, userId, uuid);

        // Obtener o crear conversación
        ConversationHistory conversation = conversationHistoryService.getOrCreateActiveConversation(
                userId, username, request.sessionId()
        );

        // Guardar mensaje del usuario
        conversationHistoryService.saveMessage(
                conversation,
                ConversationMessage.MessageRole.USER,
                request.message(),
                null
        );

        // Obtener historial de la conversación
        List<String> conversationHistory = buildConversationHistory(conversation);

        // Construir contexto adicional
        Map<String, Object> context = buildContext(username, userId);

        String rawAiResponse = aiService.generateResponse(
                request.message(),
                username,
                conversationHistory,
                context
        );
        log.debug("Raw AI response for user {}: {}", username, rawAiResponse);

        Optional<AttentionExtractionResult> extracted = attentionDataExtractor.extract(rawAiResponse);
        String aiResponse = attentionDataExtractor.stripActionBlock(rawAiResponse);

        String intent = extracted.isPresent() ? "CREATE_ATTENTION" : detectIntent(request.message());
        String action = null;
        Long attentionId = null;

        if (extracted.isPresent()) {
            try {
                AttentionRequestDto attentionRequest = toAttentionRequest(extracted.get(), userId);
                AttentionResponseDto created = admissionsIntegrationService.createAttention(attentionRequest);
                action = "ATTENTION_CREATED";
                attentionId = created.id();
                log.info("Attention {} created via AI assistant for patient {}", attentionId, extracted.get().patientId());
            } catch (Exception e) {
                log.error("Failed to create attention from AI extraction for patient {}: {}",
                        extracted.get().patientId(), e.getMessage());
                action = "ATTENTION_CREATION_FAILED";
                aiResponse = aiResponse + "\n\n⚠️ No pude registrar la atención en el sistema: " + sanitizeErrorMessage(e);
            }
        }

        String metadata = buildMetadata(intent, action, attentionId);
        conversationHistoryService.saveMessage(
                conversation,
                ConversationMessage.MessageRole.ASSISTANT,
                aiResponse,
                metadata
        );

        log.info("Successfully processed message for user: {}", username);

        return new ChatResponseDto(
                conversation.getSessionId(),
                aiResponse,
                username,
                intent,
                action,
                attentionId
        );
    }

    /**
     * Obtiene el historial de conversaciones de un usuario
     */
    @Transactional(readOnly = true)
    public List<ConversationHistoryDto> getUserHistory() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("User context not available for history request");
            throw new RuntimeException("User authentication required");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        if (userId == null) {
            userId = generateUserIdFromUuid(userDetails.getUuid());
        }

        log.debug("Fetching conversation history for userId: {}", userId);
        return conversationHistoryService.getUserConversationHistory(userId);
    }

    /**
     * Cierra una conversación
     */
    @Transactional
    public void closeConversation(String sessionId) {
        log.info("Closing conversation with sessionId: {}", sessionId);
        conversationHistoryService.closeConversation(sessionId);
    }

    /**
     * Construye el historial de mensajes para contexto
     */
    private List<String> buildConversationHistory(ConversationHistory conversation) {
        List<ConversationMessage> messages = conversationHistoryService.getConversationMessages(
                conversation.getId()
        );

        // Convertir a lista de strings alternando USER/ASSISTANT
        return messages.stream()
                .map(ConversationMessage::getContent)
                .collect(Collectors.toList());
    }

    /**
     * Construye el contexto adicional para el prompt
     */
    private Map<String, Object> buildContext(String username, Long userId) {
        Map<String, Object> context = new HashMap<>();
        context.put("username", username);
        context.put("user_id", userId);
        context.put("message", ""); // Se llenará en el AIService
        context.put("ddl", ""); // Para futuras integraciones con SQL
        context.put("sql", ""); // Para resultados de queries
        return context;
    }

    /**
     * Detecta el intent del mensaje (versión básica)
     * TODO: Mejorar con NLP o análisis más sofisticado
     */
    private String detectIntent(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("crear atención") ||
                lowerMessage.contains("nueva atención") ||
                lowerMessage.contains("registrar atención")) {
            return "CREATE_ATTENTION";
        }

        if (lowerMessage.contains("consultar") ||
                lowerMessage.contains("buscar") ||
                lowerMessage.contains("ver atención")) {
            return "QUERY_ATTENTION";
        }

        return "GENERAL_CONVERSATION";
    }

    /**
     * Genera un userId consistente a partir del UUID
     * Usa el hashCode del UUID para generar un Long positivo
     */
    private Long generateUserIdFromUuid(String uuid) {
        if (uuid == null) {
            return 0L; // Sistema por defecto
        }
        // Usar hashCode pero asegurar que sea positivo
        return (long) Math.abs(uuid.hashCode());
    }

    private AttentionRequestDto toAttentionRequest(AttentionExtractionResult extracted, Long userId) {
        List<HealthProviderRequestDto> providers = extracted.healthProviders().stream()
                .map(hp -> new HealthProviderRequestDto(hp.nit(), hp.contractId(), null, null, null))
                .collect(Collectors.toList());

        return new AttentionRequestDto(
                extracted.patientId(),
                extracted.doctorId(),
                extracted.configurationServiceId(),
                "CREATED",
                extracted.cause() != null ? extracted.cause().toUpperCase() : null,
                providers,
                null,
                extracted.triageLevel() != null ? extracted.triageLevel().toUpperCase() : null,
                extracted.entryMethod(),
                null,
                extracted.observations(),
                null,
                userId
        );
    }

    private String sanitizeErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "error desconocido";
        // Evitar exponer detalles internos del stack al usuario
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private String buildMetadata(String intent, String action, Long attentionId) {
        if (intent == null && action == null && attentionId == null) {
            return null;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (intent != null) metadata.put("intent", intent);
        if (action != null) metadata.put("action", action);
        if (attentionId != null) metadata.put("attention_id", attentionId);

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata: {}", e.getMessage());
            return null;
        }
    }
}