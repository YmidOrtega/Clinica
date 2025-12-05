package com.ClinicaDeYmid.auth_service.module.user.controller;

import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.RoleDTO;
import com.ClinicaDeYmid.auth_service.module.user.service.UserGetService;
import com.ClinicaDeYmid.auth_service.module.user.service.UserRecordService;
import com.ClinicaDeYmid.auth_service.module.user.service.UserStatusService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserRecordService userRecordService;

    @Mock
    private UserGetService userGetService;

    @Mock
    private UserStatusService userStatusService;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Test
    void createUser_Success() throws Exception {
        UserRequestDTO request = new UserRequestDTO(
                "username", java.time.LocalDate.of(1990, 1, 1), "email@test.com", "password", "+1234567890", 1L
        );
        
        RoleDTO roleDto = new RoleDTO(1L, "ROLE_USER");

        UserResponseDTO response = new UserResponseDTO(
                "uuid", "username", "email@test.com", true, com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser.ACTIVE, roleDto
        ); 

        when(userRecordService.createUser(any(UserRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("email@test.com"));
    }

    @Test
    void getUserById_Success() throws Exception {
        RoleDTO roleDto = new RoleDTO(1L, "ROLE_USER");
        UserResponseDTO response = new UserResponseDTO(
                "uuid", "username", "email@test.com", true, com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser.ACTIVE, roleDto
        );

        when(userGetService.getUserById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("username"));
    }
}
