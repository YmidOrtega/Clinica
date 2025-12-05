package com.ClinicaDeYmid.admissions_service.module.controller;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttentionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AttentionRecordService attentionRecordService;
    @Mock
    private AttentionGetService attentionGetService;
    @Mock
    private AttentionEnrichmentService attentionEnrichmentService;
    @Mock
    private AttentionStatusService attentionStatusService;

    @InjectMocks
    private AttentionController attentionController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(attentionController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should create attention successfully")
    void createAttention_Success() throws Exception {
        CompanionDto companionDto = new CompanionDto("John Doe", "12345", "Father");
        HealthProviderRequestDto hpRequestDto = new HealthProviderRequestDto("NIT123", "EPS");

        AttentionRequestDto requestDto = new AttentionRequestDto(
                null, 1L, 2L, 3L, AttentionStatus.IN_PROGRESS, Cause.ACCIDENT,
                List.of(hpRequestDto), List.<String>of(), TriageLevel.YELLOW, "ER",
                companionDto, Collections.<AuthorizationRequestDto>emptyList(), 4L
        );

        AttentionResponseDto responseDto = new AttentionResponseDto(
                1L, true, false, true, false, false,
                new ConfigurationServiceResponseDto(3L, "Service Name", "Care Type Name", "Location Name", true),
                new GetPatientDto("12345", "Patient Name", "LastName", "1990-01-01", "MALE", "Engineer", "HP123", "City", "Address", "Mobile"),
                new GetDoctorDto("67890", "Doctor Name", "LastName", Collections.emptyList()),
                List.of(new GetHealthProviderDto("NIT123", "HP Name", "EPS", null)), // Dummy HP
                null, Collections.emptyList(), Collections.emptyList(),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), null,
                AttentionStatus.IN_PROGRESS, Cause.ACCIDENT, "ER",
                Collections.emptyList(), TriageLevel.YELLOW, companionDto, "Observations"
        );

        when(attentionRecordService.createAttention(any(AttentionRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated());
    }
}
