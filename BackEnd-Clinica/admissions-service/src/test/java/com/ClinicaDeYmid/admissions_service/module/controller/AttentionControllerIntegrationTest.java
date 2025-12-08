package com.ClinicaDeYmid.admissions_service.module.controller;

import com.ClinicaDeYmid.admissions_service.infra.security.JwtAuthenticationFilter;
import com.ClinicaDeYmid.admissions_service.infra.security.SecurityConfig;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.CompanionDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.ConfigurationServiceResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionEnrichmentService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionGetService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionRecordService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttentionController.class)
@Import(SecurityConfig.class)
class AttentionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttentionRecordService attentionRecordService;
    @MockBean
    private AttentionGetService attentionGetService;
    @MockBean
    private AttentionEnrichmentService attentionEnrichmentService;
    @MockBean
    private AttentionStatusService attentionStatusService;
    @MockBean
    private com.ClinicaDeYmid.admissions_service.module.service.PdfGeneratorService pdfGeneratorService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private com.ClinicaDeYmid.admissions_service.infra.security.JwtTokenProvider jwtTokenProvider;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Should create attention successfully when authorized")
    void createAttention_Authorized_Success() throws Exception {
        CompanionDto companionDto = new CompanionDto("John Doe", "12345", "Father");
        HealthProviderRequestDto hpRequestDto = new HealthProviderRequestDto("NIT123", 1L);

        AttentionRequestDto requestDto = new AttentionRequestDto(
                null, 1L, 2L, 3L, AttentionStatus.CREATED, Cause.ACCIDENT,
                List.of(hpRequestDto), List.<String>of(), TriageLevel.YELLOW, "ER",
                companionDto, "Observations", Collections.<AuthorizationRequestDto>emptyList(), 4L
        );

        AttentionResponseDto responseDto = new AttentionResponseDto(
                1L, true, false, true, false, false,
                new ConfigurationServiceResponseDto(3L, "Service Name", "Care Type Name", "Location Name", true),
                new GetPatientDto("12345", "Patient Name", "LastName", "1990-01-01", "MALE", "Engineer", "HP123", "City", "Address", "Mobile"),
                new GetDoctorDto("67890", "Doctor Name", "LastName", Collections.emptyList()),
                List.of(new GetHealthProviderDto("NIT123", "HP Name", "EPS", null)),
                null, Collections.emptyList(), Collections.emptyList(),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), null,
                AttentionStatus.CREATED, Cause.ACCIDENT, "ER",
                Collections.emptyList(), TriageLevel.YELLOW, companionDto, "Observations"
        );

        when(attentionRecordService.createAttention(any(AttentionRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/attentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should return 403 FORBIDDEN when not authorized")
    void createAttention_Unauthorized_Returns403() throws Exception {
        CompanionDto companionDto = new CompanionDto("John Doe", "12345", "Father");
        HealthProviderRequestDto hpRequestDto = new HealthProviderRequestDto("NIT123", 1L);

        AttentionRequestDto requestDto = new AttentionRequestDto(
                null, 1L, 2L, 3L, AttentionStatus.CREATED, Cause.ACCIDENT,
                List.of(hpRequestDto), List.<String>of(), TriageLevel.YELLOW, "ER",
                companionDto, "Observations", Collections.<AuthorizationRequestDto>emptyList(), 4L
        );

        mockMvc.perform(post("/api/v1/attentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isForbidden());
    }
}
