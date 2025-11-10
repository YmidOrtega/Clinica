package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.RoleDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.RoleRepository;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRecordServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserRecordService userRecordService;

    private UserRequestDTO userRequestDTO;
    private User user;
    private Role role;
    private UserResponseDTO userResponseDTO;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setName("ADMIN");

        userRequestDTO = new UserRequestDTO(
                "usernameTest",
                LocalDate.of(1990, 1, 1),
                "test@example.com",
                "password123",
                "1234567890",
                1L
        );

        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("usernameTest");
        user.setPassword("encodedPassword");
        user.setRole(role);

        userResponseDTO = new UserResponseDTO(
                "some-uuid",
                "usernameTest",
                "test@example.com",
                true,
                StatusUser.ACTIVE,
                new RoleDTO(1L, "ADMIN")
        );
    }

    @Test
    @DisplayName("Should create a user successfully")
    void createUser_success() {
        when(userRepository.existsByEmail(userRequestDTO.email())).thenReturn(false);
        when(userRepository.existsByUsername(userRequestDTO.username())).thenReturn(false);
        when(roleRepository.findById(userRequestDTO.roleId())).thenReturn(Optional.of(role));
        when(userMapper.toEntity(any(UserRequestDTO.class), any(Role.class))).thenReturn(user);
        when(passwordEncoder.encode(userRequestDTO.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toUserResponseDTO(any(User.class))).thenReturn(userResponseDTO);

        UserResponseDTO result = userRecordService.createUser(userRequestDTO);

        assertNotNull(result);
        assertEquals(userResponseDTO.email(), result.email());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when email already exists")
    void createUser_duplicateEmail() {
        when(userRepository.existsByEmail(userRequestDTO.email())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userRecordService.createUser(userRequestDTO);
        });

        assertEquals("Ya existe un usuario con el email: test@example.com", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when username already exists")
    void createUser_duplicateUsername() {
        when(userRepository.existsByEmail(userRequestDTO.email())).thenReturn(false);
        when(userRepository.existsByUsername(userRequestDTO.username())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userRecordService.createUser(userRequestDTO);
        });

        assertEquals("Ya existe un usuario con el username: usernameTest", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when role not found")
    void createUser_roleNotFound() {
        when(userRepository.existsByEmail(userRequestDTO.email())).thenReturn(false);
        when(userRepository.existsByUsername(userRequestDTO.username())).thenReturn(false);
        when(roleRepository.findById(userRequestDTO.roleId())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userRecordService.createUser(userRequestDTO);
        });

        assertEquals("Rol con ID 1 no encontrado", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on DataIntegrityViolationException during save")
    void createUser_dataIntegrityViolation() {
        when(userRepository.existsByEmail(userRequestDTO.email())).thenReturn(false);
        when(userRepository.existsByUsername(userRequestDTO.username())).thenReturn(false);
        when(roleRepository.findById(userRequestDTO.roleId())).thenReturn(Optional.of(role));
        when(userMapper.toEntity(any(UserRequestDTO.class), any(Role.class))).thenReturn(user);
        when(passwordEncoder.encode(userRequestDTO.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenThrow(DataIntegrityViolationException.class);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userRecordService.createUser(userRequestDTO);
        });

        assertEquals("Ya existe un usuario con el email o username proporcionado", exception.getMessage());
    }
}
