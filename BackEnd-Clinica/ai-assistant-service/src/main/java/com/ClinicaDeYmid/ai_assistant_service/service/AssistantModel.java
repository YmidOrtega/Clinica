package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.shared.AssistantException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
class AssistantModel {

    record Turn(boolean fromUser, String content) {
    }

    private static final Logger log = LoggerFactory.getLogger(AssistantModel.class);
    private static final Pattern THINKING = Pattern.compile("(?s)<think>.*?</think>\\s*");

    private final ChatClient chat;
    private final InvoiceTools tools;
    private final ActionTools actionTools;
    private final String systemPrompt;
    private final Clock clock;

    AssistantModel(ChatClient.Builder chat, InvoiceTools tools, ActionTools actionTools,
                   @Value("classpath:/prompts/system.st") Resource systemPrompt, Clock clock) {
        this.chat = chat.build();
        this.tools = tools;
        this.actionTools = actionTools;
        this.systemPrompt = read(systemPrompt);
        this.clock = clock;
    }

    String answer(UUID ownerUuid, UUID conversationUuid, String userName, List<Turn> history, String question,
                  List<UUID> proposed) {
        List<Message> messages = history.stream()
                .<Message>map(turn -> turn.fromUser() ? new UserMessage(turn.content()) : new AssistantMessage(turn.content()))
                .toList();
        String reply;
        try {
            reply = chat.prompt()
                    .system(system -> system.text(systemPrompt)
                            .param("today", LocalDate.now(clock).toString())
                            .param("user", userName == null ? "" : userName))
                    .messages(messages)
                    .user(question)
                    .tools(tools, actionTools)
                    .toolContext(Map.of(ActionTools.OWNER, ownerUuid, ActionTools.CONVERSATION, conversationUuid,
                            ActionTools.PROPOSED, proposed))
                    .call()
                    .content();
        } catch (RuntimeException unreachable) {
            log.warn("The local model failed: {}", unreachable.getClass().getSimpleName());
            throw new AssistantException.ModelUnavailable();
        }
        String cleaned = reply == null ? "" : THINKING.matcher(reply).replaceAll("").strip();
        if (cleaned.isEmpty()) {
            log.warn("The local model returned an empty answer");
            throw new AssistantException.ModelUnavailable();
        }
        return cleaned;
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException missing) {
            throw new UncheckedIOException("The system prompt is missing", missing);
        }
    }
}
