package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.Authorization;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    private AttentionRequestDto attentionRequestDto;
    private Attention attention;
    private ConfigurationService configurationService;
    private AttentionResponseDto attentionResponseDto;

    @BeforeEach
    void setUp() {
        AuthorizationRequestDto authRequestDto = new AuthorizationRequestDto(
                1L, "AUTH123", TypeOfAuthorization.SPECIALIZED_SERVICES, "Authorizer", 100.0, Collections.singletonList(1L)
        );

        attentionRequestDto = new AttentionRequestDto(
                1L,
                1L,
                1L,
                1L,
                AttentionStatus.CREATED,
                Cause.ACCIDENT,
                Collections.emptyList(), // healthProviders
                Collections.emptyList(),
                TriageLevel.YELLOW,
                "Walk-in",
                null,
                "Observation",
                Collections.singletonList(authRequestDto),
                1L
        );

        configurationService = new ConfigurationService();
        configurationService.setId(1L);

        attention = new Attention();
        attention.setId(1L);
        attention.setConfigurationService(configurationService);
        attention.setPatientId(1L); // Important for validaton
        attention.setDoctorId(1L);  // Important for validation

        attentionResponseDto = new AttentionResponseDto(
                1L, true, false, true, false, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void createAttention_Success() {
        // Arrange
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(doctorClient.getDoctorById(anyLong())).thenReturn(new GetDoctorDto("123", "Dr", "House", null));
        when(userClient.getUserById(anyLong())).thenReturn(new GetUserDto("uuid", "user", "email", true, null));
        
        when(configurationServiceRepository.findByIdWithRelations(anyLong())).thenReturn(Optional.of(configurationService));
        when(attentionMapper.toEntity(any(AttentionRequestDto.class))).thenReturn(attention);
        when(authorizationMapper.toEntity(any(AuthorizationRequestDto.class))).thenReturn(new Authorization());
        when(attentionRepository.save(any(Attention.class))).thenReturn(attention);
        when(attentionEnrichmentService.enrichAttentionResponseDto(any(Attention.class))).thenReturn(attentionResponseDto);

        // Act
        AttentionResponseDto result = attentionRecordService.createAttention(attentionRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals(attentionResponseDto.id(), result.id());
        verify(attentionRepository, times(1)).save(any(Attention.class));
    }

    @Test
    void createAttention_PatientNotFound() {
        // Arrange
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(null);

        // Act & Assert
        assertThrows(Exception.class, () -> attentionRecordService.createAttention(attentionRequestDto));
    }

    @Test
    void createAttention_DoctorNotFound() {
        // Arrange
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(doctorClient.getDoctorById(anyLong())).thenReturn(null);

        // Act & Assert
        assertThrows(Exception.class, () -> attentionRecordService.createAttention(attentionRequestDto));
    }

    @Test
    void updateAttention_Success() {
        // Arrange
        Long attentionId = 1L;
        when(attentionRepository.findById(attentionId)).thenReturn(Optional.of(attention));
        // Mocks for validation (only changed fields are validated, but here IDs are same as in DTO so usually no validation if no change)
        // Wait, logic says: validate if changed.
        // DTO has patientId=1L, entity has 1L. No change.
        // However, userId is validated if present
        when(userClient.getUserById(anyLong())).thenReturn(new GetUserDto("uuid", "user", "email", true, null));
        when(authorizationMapper.toEntity(any(AuthorizationRequestDto.class))).thenReturn(new Authorization());
        
        when(attentionRepository.save(any(Attention.class))).thenReturn(attention);
        when(attentionEnrichmentService.enrichAttentionResponseDto(any(Attention.class))).thenReturn(attentionResponseDto);

        // Act
        AttentionResponseDto result = attentionRecordService.updateAttention(attentionId, attentionRequestDto);

        // Assert
        assertNotNull(result);
        verify(attentionRepository, times(1)).save(any(Attention.class));
    }

    @Test
    void updateAttention_NotFound() {
        // Arrange
        Long attentionId = 99L;
        when(attentionRepository.findById(attentionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> attentionRecordService.updateAttention(attentionId, attentionRequestDto));
    }

    @Test
    void canUpdateAttention_True() {
        // Arrange
        Long attentionId = 1L;
        attention.setInvoiced(false);
        when(attentionRepository.findById(attentionId)).thenReturn(Optional.of(attention));

        // Act
        boolean result = attentionRecordService.canUpdateAttention(attentionId);

        // Assert
        assertTrue(result);
    }

    @Test
    void canUpdateAttention_False() {
        // Arrange
        Long attentionId = 1L;
        attention.setInvoiced(true);
        when(attentionRepository.findById(attentionId)).thenReturn(Optional.of(attention));

        // Act
        boolean result = attentionRecordService.canUpdateAttention(attentionId);

        // Assert
        assertFalse(result);
    }
}
