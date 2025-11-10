package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

        attentionResponseDto = new AttentionResponseDto(
                1L, false, false, false, false, false, null, null, null, null, 1L,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    @DisplayName("Debería devolver una atención cuando se encuentra por ID")
    void getAttentionById_shouldReturnAttention_whenFound() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));
        when(attentionEnrichmentService.enrichAttentionResponseDto(attention)).thenReturn(attentionResponseDto);

        // Act
        AttentionResponseDto result = attentionGetService.getAttentionById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(attentionRepository).findById(1L);
        verify(attentionEnrichmentService).enrichAttentionResponseDto(attention);
    }

    @Test
    @DisplayName("Debería lanzar EntityNotFoundException cuando la atención no se encuentra por ID")
    void getAttentionById_shouldThrowException_whenNotFound() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> attentionGetService.getAttentionById(1L)
        );

        assertEquals("Attention not found with ID: 1", exception.getMessage());
        verify(attentionRepository).findById(1L);
        verify(attentionEnrichmentService, never()).enrichAttentionResponseDto(any());
    }

    @Test
    @DisplayName("Debería devolver resultados paginados al buscar atenciones")
    void searchAttentions_shouldReturnPagedResults_whenCalled() {
        // Arrange
        AttentionSearchRequest searchRequest = new AttentionSearchRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10, "admissionDateTime", "desc");
        Page<Attention> attentionPage = new PageImpl<>(List.of(attention));
        when(attentionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(attentionPage);
        when(attentionEnrichmentService.enrichAttentionResponseDto(attention)).thenReturn(attentionResponseDto);

        // Act
        Page<AttentionResponseDto> result = attentionGetService.searchAttentions(searchRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(attentionResponseDto, result.getContent().get(0));
        verify(attentionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Debería devolver una página vacía cuando no se encuentran resultados")
    void searchAttentions_shouldReturnEmptyPage_whenNoResultsFound() {
        // Arrange
        AttentionSearchRequest searchRequest = new AttentionSearchRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10, "admissionDateTime", "desc");
        Page<Attention> emptyPage = new PageImpl<>(Collections.emptyList());
        when(attentionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        Page<AttentionResponseDto> result = attentionGetService.searchAttentions(searchRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(attentionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Debería lanzar ValidationException para un rango de fechas inválido")
    void searchAttentions_shouldThrowValidationException_forInvalidDateRange() {
        // Arrange
        AttentionSearchRequest searchRequest = new AttentionSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, LocalDate.now(), LocalDate.now().minusDays(1), // Invalid range
                null, 0, 10, "admissionDateTime", "desc"
        );

        // Act & Assert
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> attentionGetService.searchAttentions(searchRequest)
        );

        assertEquals("Discharge date from cannot be after discharge date to", exception.getMessage());
        verify(attentionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
