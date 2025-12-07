package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.infra.security.CustomUserDetails;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationHistory;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationMessage;
import com.ClinicaDeYmid.ai_assistant_service.infra.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final AIService aiService;
    private final ConversationHistoryService conversationHistoryService;
    private final AdmissionsIntegrationService admissionsIntegrationService;

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

        // Generar respuesta con Gemini
        String aiResponse = aiService.generateResponse(
                request.message(),
                username,
                conversationHistory,
                context
        );

        // Detectar intent y acciones (aquí puedes agregar lógica más sofisticada)
        String intent = detectIntent(request.message());
        String action = null;
        Long attentionId = null;

        // Si el intent es crear atención, ejecutar acción
        if ("CREATE_ATTENTION".equals(intent)) {
            // TODO: Extraer datos del mensaje y crear atención
            // attentionId = createAttentionFromMessage(request.message(), userId);
            action = "ATTENTION_CREATED";
        }

        // Guardar respuesta del asistente
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

    /**
     * Construye metadata en formato JSON
     */
    private String buildMetadata(String intent, String action, Long attentionId) {
        if (intent == null && action == null && attentionId == null) {
            return null;
        }

        StringBuilder metadata = new StringBuilder("{");

        if (intent != null) {
            metadata.append("\"intent\":\"").append(intent).append("\"");
        }

        if (action != null) {
            if (metadata.length() > 1) metadata.append(",");
            metadata.append("\"action\":\"").append(action).append("\"");
        }

        if (attentionId != null) {
            if (metadata.length() > 1) metadata.append(",");
            metadata.append("\"attention_id\":").append(attentionId);
        }

        metadata.append("}");
        return metadata.toString();
    }
}