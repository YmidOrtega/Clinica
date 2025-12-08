package com.ClinicaDeYmid.ai_assistant_service.module.controller;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.ClinicaDeYmid.ai_assistant_service.infra.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.ClinicaDeYmid.ai_assistant_service.infra.security.JwtTokenProvider jwtTokenProvider;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void chat_Success() throws Exception {
        // Arrange
        ChatRequestDto request = new ChatRequestDto("Hello", "session-123");
        ChatResponseDto response = new ChatResponseDto("session-123", "Hi", "user", "intent", null, null);
        
        when(chatService.processMessage(any(ChatRequestDto.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/ai-assistant/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("session-123"))
                .andExpect(jsonPath("$.message").value("Hi"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getHistory_Success() throws Exception {
        // Arrange
        ConversationHistoryDto historyDto = new ConversationHistoryDto(1L, "session-123", null, true, null, null, null);
        when(chatService.getUserHistory()).thenReturn(List.of(historyDto));

        // Act & Assert
        mockMvc.perform(get("/api/v1/ai-assistant/history")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].session_id").value("session-123"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void closeConversation_Success() throws Exception {
        // Arrange
        String sessionId = "session-123";
        doNothing().when(chatService).closeConversation(sessionId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/ai-assistant/conversation/{sessionId}", sessionId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser // Any user can access health check if public, but controller has PreAuthorize on other methods. Health check has no PreAuthorize.
    void health_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/ai-assistant/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}