package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.infra.security.JwtAuthenticationFilter;
import com.ClinicaDeYmid.patient_service.infra.security.SecurityConfig;
import com.ClinicaDeYmid.patient_service.module.dto.GetClientDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.service.GetPatientInformationService;
import com.ClinicaDeYmid.patient_service.module.service.PatientRecordService;
import com.ClinicaDeYmid.patient_service.module.service.PatientSearchService;
import com.ClinicaDeYmid.patient_service.module.service.UpdatePatientInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientRecordService patientRecordService;

    @MockBean
    private GetPatientInformationService getPatientInformationService;

    @MockBean
    private UpdatePatientInformationService updatePatientInformationService;

    @MockBean
    private PatientSearchService patientSearchService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setup() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getPatient_Authorized_Success() throws Exception {
        String id = "123";
        
        GetClientDto clientDto = new GetClientDto("EPS Salud", "EPS");

        GetPatientDto dto = new GetPatientDto(
                "uuid", "CC", "123", "John", "Doe", LocalDate.of(1990, 1, 1), "Bogota", "Bogota", 
                "NONE", "ES", "M", "Eng", "S", "C", "C", "AFF", clientDto, "POL", "Mom", "Dad", 
                "U", "Loc", "Addr", "123", "321", "mail@mail.com"
        );

        when(getPatientInformationService.getPatientDto(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/patients/{identificationNumber}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificationNumber").value("123"));
    }

    @Test
    void getPatient_Unauthorized_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/patients/123"))
                .andExpect(status().isForbidden());
    }
}
