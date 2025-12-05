package com.ClinicaDeYmid.ai_assistant_service.module.controller;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.service.AIService;
import com.ClinicaDeYmid.ai_assistant_service.module.service.AdmissionsIntegrationService;
import com.ClinicaDeYmid.ai_assistant_service.module.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AIService aiService;

    @Mock
    private ChatService chatService;

    @Mock
    private AdmissionsIntegrationService admissionsIntegrationService;

    @InjectMocks
    private AgentController agentController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void chat_Success() throws Exception {
        ChatRequestDto request = new ChatRequestDto("Hello", "session-123");
        ChatResponseDto response = new ChatResponseDto("session-123", "Hi there!", "User", "GREETING", null, null);

        when(chatService.processMessage(any(ChatRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai-assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hi there!"));
    }
}
