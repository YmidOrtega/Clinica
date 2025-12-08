package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.feignclient.AdmissionsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionsIntegrationServiceTest {

    @Mock
    private AdmissionsClient admissionsClient;

    @InjectMocks
    private AdmissionsIntegrationService admissionsIntegrationService;

    @Test
    void createAttention_Success() {
        // Arrange
        AttentionRequestDto request = new AttentionRequestDto(1L, 1L, 1L, null, null, null, null, null, null, null, null, null, 1L);
        AttentionResponseDto expectedResponse = new AttentionResponseDto(1L, 1L, 1L, "CREATED", "ATT-001", "ACCIDENT", "YELLOW", "Walk-in", true, false, "2024-01-01");
        
        when(admissionsClient.createAttention(request)).thenReturn(expectedResponse);

        // Act
        AttentionResponseDto response = admissionsIntegrationService.createAttention(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(admissionsClient).createAttention(request);
    }

    @Test
    void createAttention_Failure() {
        // Arrange
        AttentionRequestDto request = new AttentionRequestDto(1L, 1L, 1L, null, null, null, null, null, null, null, null, null, 1L);
        when(admissionsClient.createAttention(request)).thenThrow(new RuntimeException("Service down"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> admissionsIntegrationService.createAttention(request));
    }

    @Test
    void getAttentionById_Success() {
        // Arrange
        Long attentionId = 1L;
        AttentionResponseDto expectedResponse = new AttentionResponseDto(1L, 1L, 1L, "CREATED", "ATT-001", "ACCIDENT", "YELLOW", "Walk-in", true, false, "2024-01-01");
        
        when(admissionsClient.getAttentionById(attentionId)).thenReturn(expectedResponse);

        // Act
        AttentionResponseDto response = admissionsIntegrationService.getAttentionById(attentionId);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(admissionsClient).getAttentionById(attentionId);
    }

    @Test
    void getAttentionById_Failure() {
        // Arrange
        Long attentionId = 1L;
        when(admissionsClient.getAttentionById(attentionId)).thenThrow(new RuntimeException("Service down"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> admissionsIntegrationService.getAttentionById(attentionId));
    }
}
