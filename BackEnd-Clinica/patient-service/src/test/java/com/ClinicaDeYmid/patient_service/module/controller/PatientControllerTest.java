package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientsListDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.service.GetPatientInformationService;
import com.ClinicaDeYmid.patient_service.module.service.PatientRecordService;
import com.ClinicaDeYmid.patient_service.module.service.PatientSearchService;
import com.ClinicaDeYmid.patient_service.module.service.UpdatePatientInformationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private GetPatientInformationService getPatientInformationService;

    @Mock
    private UpdatePatientInformationService updatePatientInformationService;

    @Mock
    private PatientSearchService patientSearchService;
    
    @InjectMocks
    private PatientController patientController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Standalone setup allows testing the controller without loading Spring context
        mockMvc = MockMvcBuilders.standaloneSetup(patientController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        
        // Register JSR310 module if needed for dates, usually auto-configured in Spring but here we might need manual config
        objectMapper.findAndRegisterModules();
    }

    @Test
    void getPatient_Success() throws Exception {
        String id = "123";
        
        com.ClinicaDeYmid.patient_service.module.dto.GetClientDto clientDto = 
            new com.ClinicaDeYmid.patient_service.module.dto.GetClientDto("EPS Salud", "EPS");

        GetPatientDto dto = new GetPatientDto(
                "uuid", "CC", "123", "John", "Doe", java.time.LocalDate.of(1990, 1, 1), "Bogota", "Bogota", 
                "NONE", "ES", "M", "Eng", "S", "C", "C", "AFF", clientDto, "POL", "Mom", "Dad", 
                "U", "Loc", "Addr", "123", "321", "mail@mail.com"
        );

        when(getPatientInformationService.getPatientDto(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/patients/{identificationNumber}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificationNumber").value("123"));
    }

    // Note: Validation tests (@Valid) might not fully work in standalone setup without extra config, 
    // but we can test the service interaction.
    
    @Test
    void searchPatients_Success() throws Exception {
        String query = "John";
        PatientsListDto dto = new PatientsListDto("123", "John", "Doe");
        Page<PatientsListDto> page = new PageImpl<>(Collections.singletonList(dto));

        when(patientSearchService.searchPatients(eq(query), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/patients/search").param("q", query))
                .andExpect(status().isOk());
    }
}
