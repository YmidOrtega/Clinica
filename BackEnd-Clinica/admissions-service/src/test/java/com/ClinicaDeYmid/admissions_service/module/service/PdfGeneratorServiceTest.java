package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.CompanionDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.ConfigurationServiceResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.ContractDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.DoctorSpecialtyDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PdfGeneratorServiceTest {

    @InjectMocks
    private PdfGeneratorService pdfGeneratorService;

    private AttentionResponseDto attentionResponseDto;

    @BeforeEach
    void setUp() {
        GetPatientDto patient = new GetPatientDto(
                "123456789", "John", "Doe",
                "01/01/1990", "M", "Engineer", "123456", "City", "Address", "1234567890"
        );

        GetDoctorDto doctor = new GetDoctorDto(
                "123", "Dr.", "House", Collections.singletonList(new DoctorSpecialtyDto("Diagnostician", 1, null))
        );

        ContractDto contract = new ContractDto(1L, "Plan", "C001", "Active");
        GetHealthProviderDto healthProvider = new GetHealthProviderDto(
                "123456789", "Provider Inc", "Address", contract
        );

        ConfigurationServiceResponseDto configService = new ConfigurationServiceResponseDto(
                1L, "Emergency", "EM01", "Main", true
        );

        CompanionDto companion = new CompanionDto("Jane Doe", "1234567890", "Sister");

        attentionResponseDto = new AttentionResponseDto(
                1L, true, false, true, false, false,
                configService, patient, doctor, Collections.singletonList(healthProvider),
                null, Collections.emptyList(), Collections.emptyList(),
                LocalDateTime.now(), null, LocalDateTime.now(),
                AttentionStatus.CREATED, null, "Walking",
                Collections.singletonList("A001"), null,
                companion, "Patient complains of leg pain"
        );
    }

    @Test
    void generateAttentionPdf_Success() {
        // Act
        byte[] result = pdfGeneratorService.generateAttentionPdf(attentionResponseDto);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}