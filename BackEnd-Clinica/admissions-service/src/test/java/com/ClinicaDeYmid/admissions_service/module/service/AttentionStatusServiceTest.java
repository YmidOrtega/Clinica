package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttentionStatusServiceTest {

    @Mock
    private AttentionRepository attentionRepository;

    @InjectMocks
    private AttentionStatusService attentionStatusService;

    private Attention attention;

    @BeforeEach
    void setUp() {
        attention = new Attention();
        attention.setId(1L);
        attention.setActive(false);
        // Default is not deleted
    }

    @Test
    void activateAttention_Success() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));

        // Act
        attentionStatusService.activateAttention(1L);

        // Assert
        assertTrue(attention.isActive());
        verify(attentionRepository, times(1)).save(attention);
    }

    @Test
    void activateAttention_NotFound() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> attentionStatusService.activateAttention(1L));
    }

    @Test
    void deactivateAttention_Success() {
        // Arrange
        attention.setActive(true);
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));

        // Act
        attentionStatusService.deactivateAttention(1L);

        // Assert
        assertFalse(attention.isActive());
        verify(attentionRepository, times(1)).save(attention);
    }

    @Test
    void softDeleteAttention_Success() {
        // Arrange
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));

        // Act
        attentionStatusService.softDeleteAttention(1L, "Reason");

        // Assert
        assertTrue(attention.isDeleted());
        verify(attentionRepository, times(1)).save(attention);
    }

    @Test
    void softDeleteAttention_AlreadyDeleted() {
        // Arrange
        attention.softDelete(1L, "Reason"); // Manually mark as deleted
        when(attentionRepository.findById(1L)).thenReturn(Optional.of(attention));

        // Act & Assert
        assertThrows(ValidationException.class, () -> attentionStatusService.softDeleteAttention(1L, "Reason"));
    }

    @Test
    void restoreAttention_Success() {
        // Arrange
        attention.softDelete(1L, "Reason");
        when(attentionRepository.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(attention));

        // Act
        attentionStatusService.restoreAttention(1L);

        // Assert
        assertFalse(attention.isDeleted());
        verify(attentionRepository, times(1)).save(attention);
    }
}