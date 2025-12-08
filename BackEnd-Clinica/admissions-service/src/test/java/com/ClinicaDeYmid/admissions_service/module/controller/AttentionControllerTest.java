package com.ClinicaDeYmid.admissions_service.module.controller;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionGetService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionRecordService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionStatusService;
import com.ClinicaDeYmid.admissions_service.module.service.PdfGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttentionController.class)
class AttentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttentionGetService attentionGetService;

    @MockBean
    private AttentionRecordService attentionRecordService;

    @MockBean
    private AttentionStatusService attentionStatusService;

    @MockBean
    private PdfGeneratorService pdfGeneratorService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.ClinicaDeYmid.admissions_service.infra.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.ClinicaDeYmid.admissions_service.infra.security.JwtTokenProvider jwtTokenProvider;

    private AttentionRequestDto attentionRequestDto;
    private AttentionResponseDto attentionResponseDto;

    @BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto healthProviderRequestDto = new com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto("NIT123", 1L);
        
        attentionRequestDto = new AttentionRequestDto(
                1L,
                1L,
                1L,
                1L,
                AttentionStatus.CREATED,
                Cause.ACCIDENT,
                Collections.singletonList(healthProviderRequestDto),
                Collections.emptyList(),
                TriageLevel.YELLOW,
                "Walk-in",
                null,
                "Observation",
                Collections.emptyList(),
                1L
        );

        attentionResponseDto = new AttentionResponseDto(
                1L, true, false, true, false, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAttention_Success() throws Exception {
        when(attentionRecordService.createAttention(any(AttentionRequestDto.class))).thenReturn(attentionResponseDto);

        mockMvc.perform(post("/api/v1/attentions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attentionRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getAttentionById_Success() throws Exception {
        when(attentionGetService.getAttentionById(1L)).thenReturn(attentionResponseDto);

        mockMvc.perform(get("/api/v1/attentions/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAttention_Success() throws Exception {
        when(attentionRecordService.updateAttention(eq(1L), any(AttentionRequestDto.class))).thenReturn(attentionResponseDto);

        mockMvc.perform(put("/api/v1/attentions/{id}", 1L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attentionRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getAttentionsByPatientId_Success() throws Exception {
        when(attentionGetService.getAttentionsByPatientId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/attentions/patient/{patientId}", 1L))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activateAttention_Success() throws Exception {
        mockMvc.perform(patch("/api/v1/attentions/{id}/activate", 1L)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void softDeleteAttention_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/attentions/{id}", 1L)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
