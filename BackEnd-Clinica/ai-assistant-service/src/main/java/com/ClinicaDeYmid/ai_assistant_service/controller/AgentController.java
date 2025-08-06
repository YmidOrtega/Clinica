package com.ClinicaDeYmid.ai_assistant_service.controller;

import com.ClinicaDeYmid.ai_assistant_service.dto.AIResponse;
import com.ClinicaDeYmid.ai_assistant_service.dto.ChatResponse;
import com.ClinicaDeYmid.ai_assistant_service.dto.RequestParams;
import com.ClinicaDeYmid.ai_assistant_service.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping ("/api/chat/ia-assistant")
@RequiredArgsConstructor
public class AgentController {

    private final AIService aiService;

    @PostMapping("/chat")

    public ResponseEntity<ChatResponse> chat(@RequestBody RequestParams requestParams) {

        String conversationId = (requestParams.user_id() == null) ?
                UUID.randomUUID().toString() : requestParams.user_id();

        AIResponse response = aiService.chat(conversationId, requestParams.message());
        return ResponseEntity.ok(new ChatResponse();
                conversationId,
                response.answer()
        );
    }

}
