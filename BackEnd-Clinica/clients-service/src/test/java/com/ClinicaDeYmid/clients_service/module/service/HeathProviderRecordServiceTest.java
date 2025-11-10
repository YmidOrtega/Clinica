package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateContractNumberException;
import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateHealthProviderNitException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderValidationException;
import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeathProviderRecordServiceTest {

    @Mock
    private HealthProviderRepository healthProviderRepository;

    @Mock
    private HealthProviderMapper healthProviderMapper;

    @Mock
    private ContractRepository contractRepository;

    @InjectMocks
    private HeathProviderRecordService heathProviderRecordService;

    private CreateHealthProviderDto createDto;
    private HealthProvider healthProvider;

    @BeforeEach
    void setUp() {
        createDto = mock(CreateHealthProviderDto.class);
        healthProvider = new HealthProvider();
        healthProvider.setNit(new Nit("123456789-0"));
        healthProvider.setSocialReason("Test Social Reason");
        healthProvider.setTypeProvider(TypeProvider.EPS);
    }

    @Test
    @DisplayName("Should create health provider successfully")
    void createHealthProvider_success() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.save(any(HealthProvider.class))).thenReturn(healthProvider);

        // Act
        HealthProvider result = heathProviderRecordService.createHealthProvider(createDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getNit().getValue()).isEqualTo("1234567890");
        verify(healthProviderRepository).save(healthProvider);
    }

    @Test
    @DisplayName("Should throw exception when NIT is null")
    void createHealthProvider_throwsException_whenNitIsNull() {
        // Arrange
        healthProvider.setNit(null);
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);

        // Act & Assert
        HealthProviderValidationException exception = assertThrows(
                HealthProviderValidationException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("El NIT es obligatorio");
        verify(healthProviderRepository, never()).save(any(HealthProvider.class));
    }

    @Test
    @DisplayName("Should throw exception when NIT is duplicated")
    void createHealthProvider_throwsException_whenNitIsDuplicated() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.findByNit_Value("1234567890")).thenReturn(Optional.of(new HealthProvider()));

        // Act & Assert
        DuplicateHealthProviderNitException exception = assertThrows(
                DuplicateHealthProviderNitException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("Ya existe un proveedor de salud registrado con el NIT: 1234567890");
        verify(healthProviderRepository, never()).save(any(HealthProvider.class));
    }

    @Test
    @DisplayName("Should throw exception when social reason is empty")
    void createHealthProvider_throwsException_whenSocialReasonIsEmpty() {
        // Arrange
        healthProvider.setSocialReason("");
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);

        // Act & Assert
        HealthProviderValidationException exception = assertThrows(
                HealthProviderValidationException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("La razón social es obligatoria");
        verify(healthProviderRepository, never()).save(any(HealthProvider.class));
    }

    @Test
    @DisplayName("Should throw exception when type provider is null")
    void createHealthProvider_throwsException_whenTypeProviderIsNull() {
        // Arrange
        healthProvider.setTypeProvider(null);
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);

        // Act & Assert
        HealthProviderValidationException exception = assertThrows(
                HealthProviderValidationException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("El tipo de proveedor es obligatorio");
        verify(healthProviderRepository, never()).save(any(HealthProvider.class));
    }

    @Test
    @DisplayName("Should throw exception when contract number is duplicated")
    void createHealthProvider_throwsException_whenContractNumberIsDuplicated() {
        // Arrange
        Contract contract = new Contract();
        contract.setContractNumber("C123");
        healthProvider.setContracts(Collections.singletonList(contract));
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(contractRepository.findByContractNumber("C123")).thenReturn(Optional.of(new Contract()));

        // Act & Assert
        DuplicateContractNumberException exception = assertThrows(
                DuplicateContractNumberException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("Ya existe un contrato registrado con el número: C123");
        verify(healthProviderRepository, never()).save(any(HealthProvider.class));
    }

    @Test
    @DisplayName("Should throw HealthProviderDataAccessException when repository fails")
    void createHealthProvider_throwsHealthProviderDataAccessException_whenRepositoryFails() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.save(any(HealthProvider.class))).thenThrow(new DataAccessResourceFailureException("DB is down"));

        // Act & Assert
        HealthProviderDataAccessException exception = assertThrows(
                HealthProviderDataAccessException.class,
                () -> heathProviderRecordService.createHealthProvider(createDto)
        );

        assertThat(exception.getMessage()).contains("Error de acceso a datos durante la operación: crear proveedor de salud");
        verify(healthProviderRepository).save(any(HealthProvider.class));
    }
}