package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.ActiveAttentionExistsException;
import com.ClinicaDeYmid.admissions_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.CompanionDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.ConfigurationServiceResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.entity.Companion;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttentionRecordServiceTest {

    @Mock
    private AttentionRepository attentionRepository;
    @Mock
    private AttentionMapper attentionMapper;
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
    @Mock
    private ConfigurationServiceRepository configurationServiceRepository;

    @InjectMocks
    private AttentionRecordService attentionRecordService;

    private AttentionRequestDto attentionRequestDto;
    private Attention attention;
    private AttentionResponseDto attentionResponseDto;
    private GetPatientDto getPatientDto;
    private GetDoctorDto getDoctorDto;
    private GetHealthProviderDto getHealthProviderDto;
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        CompanionDto companionDto = new CompanionDto("John Doe", "12345", "Father");
        Companion companion = Companion.builder().fullName("John Doe").build();

        HealthProviderRequestDto hpRequestDto = new HealthProviderRequestDto("NIT123", "EPS");

        attentionRequestDto = new AttentionRequestDto(
                null, 1L, 2L, 3L, AttentionStatus.IN_PROGRESS, Cause.ACCIDENT,
                List.of(hpRequestDto), List.<String>of(), TriageLevel.YELLOW, "ER",
                companionDto, Collections.<AuthorizationRequestDto>emptyList(), 4L
        );

        attention = new Attention();
        attention.setId(100L);
        attention.setPatientId(1L);
        attention.setDoctorId(2L);
        attention.setStatus(AttentionStatus.IN_PROGRESS);
        attention.setCause(Cause.ACCIDENT);
        attention.setCompanion(companion);
        attention.setCreatedAt(LocalDateTime.now());
        attention.setCreatedBy(4L);
        attention.setConfigurationService(new ConfigurationService());
        attention.getConfigurationService().setId(3L);


        attentionResponseDto = new AttentionResponseDto(
                100L, true, false, true, false, false,
                new ConfigurationServiceResponseDto(3L, "Service Name", "Care Type Name", "Location Name", true),
                new GetPatientDto("12345", "Patient Name", "LastName", "1990-01-01", "MALE", "Engineer", "HP123", "City", "Address", "Mobile"),
                new GetDoctorDto("67890", "Doctor Name", "LastName", Collections.emptyList()),
                List.of(new GetHealthProviderDto("NIT123", "HP Name", "EPS", null)),
                null, Collections.emptyList(), Collections.emptyList(),
                LocalDateTime.now(), LocalDateTime.now(), null,
                AttentionStatus.IN_PROGRESS, Cause.ACCIDENT, "ER",
                Collections.emptyList(), TriageLevel.YELLOW, companionDto, "Observations"
        );

        getPatientDto = new GetPatientDto("12345", "Patient Name", "LastName", "1990-01-01", "MALE", "Engineer", "HP123", "City", "Address", "Mobile");
        getDoctorDto = new GetDoctorDto("67890", "Doctor Name", "LastName", Collections.emptyList());
        getHealthProviderDto = new GetHealthProviderDto("NIT123", "HP Name", "EPS", null);
        configurationService = new ConfigurationService();
        configurationService.setId(3L);
        configurationService.setActive(true);
    }

    @Test
    @DisplayName("Should create attention successfully")
    void createAttention_Success() {
        try (MockedStatic<UserContextHolder> mockedStatic = mockStatic(UserContextHolder.class)) {
            mockedStatic.when(UserContextHolder::getCurrentUserId).thenReturn(4L);

            // Mocks for AttentionRecordService's internal calls (assuming existing methods based on service code)
            // NOTE: These methods might not align with the *declared* interfaces of the mocks due to base code inconsistencies.
            // We mock them as they are *called* by the AttentionRecordService.
            when(attentionRepository.existsByPatientIdAndIsActiveAttentionTrue(anyLong())).thenReturn(false); 
            when(patientClient.getPatientById(anyLong())).thenReturn(getPatientDto); 
            when(doctorClient.getDoctorById(anyLong())).thenReturn(getDoctorDto);
            when(healthProviderClient.getHealthProviderByNit(anyString())).thenReturn(getHealthProviderDto); 
            when(configurationServiceRepository.findByIdAndActiveTrue(anyLong())).thenReturn(Optional.of(configurationService)); 

            when(attentionMapper.toEntity(any(AttentionRequestDto.class))).thenReturn(attention);
            when(attentionRepository.save(any(Attention.class))).thenReturn(attention);
            when(attentionMapper.toResponseDto(any(Attention.class))).thenReturn(attentionResponseDto);

            AttentionResponseDto result = attentionRecordService.createAttention(attentionRequestDto);

            assertNotNull(result);
            assertEquals(100L, result.getId());
            verify(attentionRepository).save(any(Attention.class));
            mockedStatic.verify(UserContextHolder::getCurrentUserId);
        }
    }

    @Test
    @DisplayName("Should throw ActiveAttentionExistsException if patient already has active attention")
    void createAttention_ActiveAttentionExists() {
        try (MockedStatic<UserContextHolder> mockedStatic = mockStatic(UserContextHolder.class)) {
            mockedStatic.when(UserContextHolder::getCurrentUserId).thenReturn(4L);

            when(attentionRepository.existsByPatientIdAndIsActiveAttentionTrue(anyLong())).thenReturn(true);

            assertThrows(ActiveAttentionExistsException.class, () ->
                    attentionRecordService.createAttention(attentionRequestDto));
            verify(attentionRepository, never()).save(any(Attention.class));
        }
    }

    @Test
    @DisplayName("Should throw DataAccessException on repository error")
    void createAttention_DataAccessException() {
        try (MockedStatic<UserContextHolder> mockedStatic = mockStatic(UserContextHolder.class)) {
            mockedStatic.when(UserContextHolder::getCurrentUserId).thenReturn(4L);

            when(attentionRepository.existsByPatientIdAndIsActiveAttentionTrue(anyLong())).thenReturn(false);
            when(patientClient.getPatientById(anyLong())).thenReturn(getPatientDto);
            when(doctorClient.getDoctorById(anyLong())).thenReturn(getDoctorDto);
            when(healthProviderClient.getHealthProviderByNit(anyString())).thenReturn(getHealthProviderDto);
            when(configurationServiceRepository.findByIdAndActiveTrue(anyLong())).thenReturn(Optional.of(configurationService));
            
            when(attentionMapper.toEntity(any(AttentionRequestDto.class))).thenReturn(attention);
            when(attentionRepository.save(any(Attention.class))).thenThrow(new DataAccessException("DB Error") {});

            assertThrows(DataAccessException.class, () ->
                    attentionRecordService.createAttention(attentionRequestDto));
        }
    }
}
