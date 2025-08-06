package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttentionRecordServiceTest {

    @Mock
    private AttentionRepository attentionRepository;
    @Mock
    private ConfigurationServiceRepository configurationServiceRepository;
    @Mock
    private AttentionMapper attentionMapper;
    @Mock
    private AuthorizationMapper authorizationMapper;
    @Mock
    private AttentionEnrichmentService attentionEnrichmentService;
    @Mock
    private PatientClient patientClient;
    @Mock
    private DoctorClient doctorClient;
    @Mock
    private HealthProviderClient healthProviderClient;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private AttentionRecordService attentionRecordService;

    private AttentionResponseDto createExpectedResponseDto() {
        return new AttentionResponseDto(
                1L,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                AttentionStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("Deberia crear una atencion correctamente")
    void createAttention() {
        // Arrange
        AttentionRequestDto requestDto = createSampleRequestDto();
        ConfigurationService configService = new ConfigurationService();
        Attention mappedAttention = new Attention();
        Attention savedAttention = new Attention();
        AttentionResponseDto expectedResponse = createExpectedResponseDto();

        when(configurationServiceRepository.findById(requestDto.configurationServiceId()))
                .thenReturn(Optional.of(configService));

        when(attentionMapper.toEntity(requestDto))
                .thenReturn(mappedAttention);

        when(attentionRepository.save(any(Attention.class)))
                .thenReturn(savedAttention);

        when(attentionEnrichmentService.enrichAttentionResponseDto(savedAttention))
                .thenReturn(expectedResponse);

        // Act

        AttentionResponseDto actualResponse = attentionRecordService.createAttention(requestDto);

        // Assert

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        verify(configurationServiceRepository, times(1)).findById(requestDto.configurationServiceId());

        verify(attentionRepository, times(1)).save(any(Attention.class));

        verify(attentionEnrichmentService, times(1)).enrichAttentionResponseDto(savedAttention);


    }

    @Test
    void updateAttention() {
    }

    @Test
    void canUpdateAttention() {
    }

    @Test
    void getInvoiceStatus() {
    }

    private AttentionRequestDto createSampleRequestDto() {
        HealthProviderRequestDto healthProvider = new HealthProviderRequestDto(
                "1234567890",
                1L
        );
        return new AttentionRequestDto(
                null,
                123L,
                85L,
                20L,
                AttentionStatus.IN_PROGRESS,
                Cause.ACCIDENT,
                List.of(healthProvider),
                null,
                null,
                null,
                null,
                "Observaciones de prueba",
                null,
                42L
        );
    }
}