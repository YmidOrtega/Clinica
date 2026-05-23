package com.ClinicaDeYmid.ai_assistant_service.module.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIService {

    @Value("classpath:/prompts/prompt.st")
    private Resource promptResource;

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Genera respuesta usando Gemini con contexto de conversación
     */
    public String generateResponse(String userMessage,
                                   String username,
                                   List<String> conversationHistory,
                                   Map<String, Object> context) {
        try {
            log.debug("Generating response for user: {}", username);

            // Cargar el prompt del sistema
            String systemPromptTemplate = loadPromptTemplate();

            // Construir el prompt con contexto
            String systemPrompt = buildSystemPrompt(systemPromptTemplate, username, context);

            // Construir lista de mensajes con historial
            List<Message> messages = buildMessageList(systemPrompt, conversationHistory, userMessage);

            // Crear prompt con todos los mensajes
            Prompt prompt = new Prompt(messages);

            // Construir ChatClient y llamar a Gemini
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();

            // Strip Qwen3/thinking-model <think>...</think> blocks if LM Studio includes them
            if (response != null) {
                response = response.replaceAll("(?s)<think>.*?</think>\\s*", "").strip();
            }

            if (response == null || response.isBlank()) {
                log.warn("AI returned empty response for user {}. Check LM Studio reasoning/thinking settings — enable 'Include reasoning in response'.", username);
                return "Lo siento, no pude generar una respuesta en este momento. Por favor intenta de nuevo.";
            }

            log.debug("Successfully generated response for user: {}", username);
            return response;

        } catch (IOException e) {
            log.error("Error loading prompt template: {}", e.getMessage(), e);
            throw new RuntimeException("Error loading AI prompt template", e);
        } catch (Exception e) {
            log.error("Error generating AI response for user {}: {}", username, e.getMessage(), e);
            throw new RuntimeException("Error generating AI response", e);
        }
    }

    private String loadPromptTemplate() throws IOException {
        return promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    private String buildSystemPrompt(String template, String username, Map<String, Object> context) {
        String prompt = template.replace("{username}", sanitizeContextValue(username));

        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? sanitizeContextValue(entry.getValue().toString()) : "";
                prompt = prompt.replace(placeholder, value);
            }
        }

        return prompt;
    }

    private String sanitizeContextValue(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("[\\{\\}\\[\\]<>]", "");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private List<Message> buildMessageList(String systemPrompt,
                                           List<String> conversationHistory,
                                           String currentUserMessage) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(systemPrompt));

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (int i = 0; i < conversationHistory.size(); i++) {
                String content = conversationHistory.get(i);
                if (i % 2 == 0) {
                    messages.add(new UserMessage(content));
                } else {
                    messages.add(new AssistantMessage(content));
                }
            }
        }
        messages.add(new UserMessage(currentUserMessage));
        return messages;
    }
}