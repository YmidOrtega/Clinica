package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
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

    public AIService(ChatClient.Builder chatBuilder) {
        this.chatClient = chatBuilder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new PromptChatMemoryAdvisor(new InMemoryChatMemory())
                ).build();
    }

    public ChatResponse chat(String username) {
        try {
            var prompt = promptText.getContentAsString(Charset.defaultCharset());
            var response = chatClient
                    .prompt()
                    .user(userSpec ->
                            userSpec.text(prompt)
                                    .param("username", username))
                    .call()
                    .entity(String.class);
            return new ChatResponse(response);
        }catch (Exception e) {
            log.error("Error processing chat request for user {}: {}", username, e.getMessage(), e);
            throw new RuntimeException("Error processing chat request", e);
        }
    }


}
