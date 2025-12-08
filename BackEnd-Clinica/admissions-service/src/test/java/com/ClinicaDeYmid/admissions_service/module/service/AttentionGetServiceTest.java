package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttentionGetServiceTest {

    @Mock
    private AttentionRepository attentionRepository;

    @Mock
    private AttentionEnrichmentService attentionEnrichmentService;

    @Mock
    private AttentionMapper attentionMapper;

    @Mock
    private PatientClient patientClient;

    @Mock
    private DoctorClient doctorClient;

    @Mock
    private HealthProviderClient healthProviderClient;

    @InjectMocks
    private AttentionGetService attentionGetService;

    private Attention attention;
    private AttentionResponseDto attentionResponseDto;

    @BeforeEach
    void setUp() {
        attention = new Attention();
        attention.setId(1L);
        attention.setPatientId(1L);
        attention.setDoctorId(1L);
        attention.setStatus(AttentionStatus.CREATED);

        attentionResponseDto = new AttentionResponseDto(
                1L, true, false, true, false, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void getAttentionById_Success() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));
        when(attentionEnrichmentService.enrichAttentionResponseDto(attention)).thenReturn(attentionResponseDto);

        // Act
        AttentionResponseDto result = attentionGetService.getAttentionById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(attentionResponseDto.id(), result.id());
    }

    @Test
    void getAttentionById_NotFound() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> attentionGetService.getAttentionById(1L));
    }

    @Test
    void getAttentionsByPatientId_Success() {
        // Arrange
        Long patientId = 1L;
        when(attentionRepository.findByPatientId(patientId)).thenReturn(Collections.singletonList(attention));
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(attentionMapper.toPatientWithAttentionsResponse(anyString(), anyList())).thenReturn(new PatientWithAttentionsResponse(null, null));

        // Act
        List<PatientWithAttentionsResponse> result = attentionGetService.getAttentionsByPatientId(patientId);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getActiveAttentionByPatientId_Success() {
        // Arrange
        Long patientId = 1L;
        when(attentionRepository.findByPatientIdAndStatus(patientId, AttentionStatus.CREATED)).thenReturn(Optional.of(attention));
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(attentionMapper.toPatientWithAttentionsResponse(anyString(), anyList())).thenReturn(new PatientWithAttentionsResponse(null, null));

        // Act
        List<PatientWithAttentionsResponse> result = attentionGetService.getActiveAttentionByPatientId(patientId);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getActiveAttentionByPatientId_NotFound() {
        // Arrange
        Long patientId = 1L;
        when(attentionRepository.findByPatientIdAndStatus(patientId, AttentionStatus.CREATED)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> attentionGetService.getActiveAttentionByPatientId(patientId));
    }

    @Test
    void searchAttentions_Success() {
        // Arrange
        AttentionSearchRequest searchRequest = new AttentionSearchRequest(
                1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10, "createdAt", "desc"
        );
        Page<Attention> attentionPage = new PageImpl<>(Collections.singletonList(attention));
        
        when(attentionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(attentionPage);
        when(attentionEnrichmentService.enrichAttentionResponseDto(any(Attention.class))).thenReturn(attentionResponseDto);

        // Act
        Page<AttentionResponseDto> result = attentionGetService.searchAttentions(searchRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }
}
