package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ClinicaDeYmid.ai_assistant_service.infra.security.CustomUserDetails;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ChatResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.ConversationHistoryDto;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationHistory;
import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private AIService aiService;

    @Mock
    private ConversationHistoryService conversationHistoryService;

    @Mock
    private AdmissionsIntegrationService admissionsIntegrationService;

    @Mock
    private AttentionDataExtractor attentionDataExtractor;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processMessage_Success() throws Exception {
        // Arrange
        String uuid = "test-uuid";
        String username = "test@example.com";
        Long userId = 1L;
        String sessionId = "session-123";
        String message = "Hello";
        String aiResponse = "Hi there!";

        ChatRequestDto requestDto = new ChatRequestDto(message, sessionId);
        CustomUserDetails userDetails = new CustomUserDetails(userId, uuid, username, "ROLE_ADMIN", Collections.emptyList());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userIdentityService.resolveUserId(userId, uuid, username)).thenReturn(userId);

        ConversationHistory conversation = new ConversationHistory();
        conversation.setId(1L);
        conversation.setSessionId(sessionId);

        when(conversationHistoryService.getOrCreateActiveConversation(userId, username, sessionId)).thenReturn(conversation);
        when(conversationHistoryService.getConversationMessages(1L)).thenReturn(Collections.emptyList());
        when(aiService.generateResponse(eq(message), eq(username), anyList(), anyMap())).thenReturn(aiResponse);
        when(attentionDataExtractor.extract(aiResponse)).thenReturn(Optional.empty());
        when(attentionDataExtractor.stripActionBlock(aiResponse)).thenReturn(aiResponse);
        doReturn("{\"intent\":\"GENERAL_CONVERSATION\"}").when(objectMapper).writeValueAsString(any());

        // Act
        ChatResponseDto response = chatService.processMessage(requestDto);

        // Assert
        assertNotNull(response);
        assertEquals(sessionId, response.sessionId());
        assertEquals(aiResponse, response.message());
        assertEquals(username, response.username());
        
        verify(conversationHistoryService).saveMessage(eq(conversation), eq(ConversationMessage.MessageRole.USER), eq(message), isNull());
        verify(conversationHistoryService).saveMessage(eq(conversation), eq(ConversationMessage.MessageRole.ASSISTANT), eq(aiResponse), anyString());
    }

    @Test
    void processMessage_UserNotAuthenticated() {
        // Arrange
        ChatRequestDto requestDto = new ChatRequestDto("Hello", "session-123");
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> chatService.processMessage(requestDto));
    }

    @Test
    void getUserHistory_Success() {
        // Arrange
        Long userId = 1L;
        String uuid = "test-uuid";
        String username = "test@example.com";
        CustomUserDetails userDetails = new CustomUserDetails(userId, uuid, username, "ROLE_ADMIN", Collections.emptyList());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userIdentityService.resolveUserId(userId, uuid, username)).thenReturn(userId);

        when(conversationHistoryService.getUserConversationHistory(userId)).thenReturn(Collections.emptyList());

        // Act
        List<ConversationHistoryDto> history = chatService.getUserHistory();

        // Assert
        assertNotNull(history);
        verify(conversationHistoryService).getUserConversationHistory(userId);
    }

    @Test
    void processMessage_ResolvesUserIdFromIdentityService() throws Exception {
        String uuid = "test-uuid";
        String username = "test@example.com";
        Long resolvedUserId = 99L;
        String sessionId = "session-123";
        String message = "Hello";
        String aiResponse = "Hi there!";

        ChatRequestDto requestDto = new ChatRequestDto(message, sessionId);
        CustomUserDetails userDetails = new CustomUserDetails(null, uuid, username, "ROLE_ADMIN", Collections.emptyList());

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userIdentityService.resolveUserId(null, uuid, username)).thenReturn(resolvedUserId);

        ConversationHistory conversation = new ConversationHistory();
        conversation.setId(1L);
        conversation.setSessionId(sessionId);

        when(conversationHistoryService.getOrCreateActiveConversation(resolvedUserId, username, sessionId)).thenReturn(conversation);
        when(conversationHistoryService.getConversationMessages(1L)).thenReturn(Collections.emptyList());
        when(aiService.generateResponse(eq(message), eq(username), anyList(), anyMap())).thenReturn(aiResponse);
        when(attentionDataExtractor.extract(aiResponse)).thenReturn(Optional.empty());
        when(attentionDataExtractor.stripActionBlock(aiResponse)).thenReturn(aiResponse);
        doReturn("{\"intent\":\"GENERAL_CONVERSATION\"}").when(objectMapper).writeValueAsString(any());

        ChatResponseDto response = chatService.processMessage(requestDto);

        assertNotNull(response);
        verify(userIdentityService).resolveUserId(null, uuid, username);
        verify(conversationHistoryService).getOrCreateActiveConversation(resolvedUserId, username, sessionId);
    }

    @Test
    void closeConversation_Success() {
        // Arrange
        String sessionId = "session-123";

        // Act
        chatService.closeConversation(sessionId);

        // Assert
        verify(conversationHistoryService).closeConversation(sessionId);
    }
}
