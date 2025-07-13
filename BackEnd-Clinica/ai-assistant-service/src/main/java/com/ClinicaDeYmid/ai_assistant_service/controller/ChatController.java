package com.ClinicaDeYmid.ai_assistant_service.controller;

import com.ClinicaDeYmid.ai_assistant_service.dto.ChatResponse;
import com.ClinicaDeYmid.ai_assistant_service.dto.RequestParams;
import com.ClinicaDeYmid.ai_assistant_service.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping ("/api/chat/ia-assistant")
@RequiredArgsConstructor
public class ChatController {

    private final AIService aiService;

    @PostMapping()
    public ResponseEntity<ChatResponse> chat(@RequestBody RequestParams requestParams) {
        var result = aiService.chat(requestParams.username());
        return ResponseEntity.ok(result);
    }

}
