package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorCreateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorGetService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorRecordService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DoctorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DoctorRecordService doctorRecordService;

    @Mock
    private DoctorGetService doctorGetService;

    @Mock
    private DoctorStatusService doctorStatusService;

    @InjectMocks
    private DoctorController doctorController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(doctorController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Test
    void createDoctor_Success() throws Exception {
        // Provide dummy values for the request DTO
        DoctorCreateRequestDTO request = new DoctorCreateRequestDTO(
                123, "John", "Doe", "12345", java.util.List.of(1L), null, "1234567890", "email@test.com", "LIC-1234", "Address", 100.0
        );

        DoctorResponseDto response = new DoctorResponseDto(
                1L, 123, "John", "Doe", "John Doe", "12345", "1234567890", "email@test.com", "LIC-1234", "Address", 100.0, true, java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), Collections.emptyList()
        );

        when(doctorRecordService.createDoctor(any(DoctorCreateRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/suppliers/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("email@test.com"));
    }

    @Test
    void getDoctorById_Success() throws Exception {
        DoctorResponseDto response = new DoctorResponseDto(
                1L, 123, "John", "Doe", "John Doe", "12345", "1234567890", "email@test.com", "LIC-123", "Address", 100.0, true, java.time.LocalDateTime.now(), java.time.LocalDateTime.now(), Collections.emptyList()
        );

        when(doctorGetService.getDoctorById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/suppliers/doctors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
