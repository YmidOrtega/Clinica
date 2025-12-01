package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;

@Service
@Slf4j
public class AIService {

    @Value("classpath:/prompts/prompt-text.st")
    private Resource promptText;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public AIService(ChatClient.Builder chatBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatBuilder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new PromptChatMemoryAdvisor(chatMemory)
                )
                .build();
    }

    /**
     * Procesa un mensaje del usuario y genera respuesta usando Gemini
     */
    public ChatResponse chat(String userId, String message, String userName) {
        try {
            log.info("Processing chat request for user: {} (ID: {})", userName, userId);

            String prompt = promptText.getContentAsString(Charset.defaultCharset());

            String response = chatClient
                    .prompt()
                    .user(userSpec ->
                            userSpec.text(prompt)
                                    .param("message", message)
                                    .param("userName", userName)
                                    .param("userId", userId))
                    .call()
                    .content();

            log.info("Chat response generated successfully for user: {}", userId);
            return new ChatResponse(userId, response);

        } catch (Exception e) {
            log.error("Error processing chat request for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error al procesar el mensaje con Gemini AI", e);
        }
    }

    /**
     * Limpia la memoria de conversación de un usuario
     */
    public void clearMemory(String userId) {
        log.info("Clearing chat memory for user: {}", userId);
        chatMemory.clear(userId);
    }
}