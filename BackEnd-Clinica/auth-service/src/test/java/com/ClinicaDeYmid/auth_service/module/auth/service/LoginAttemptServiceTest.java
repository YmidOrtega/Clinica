package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.entity.LoginAttempt;
import com.ClinicaDeYmid.auth_service.module.auth.repository.LoginAttemptRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService - Unit Tests")
class LoginAttemptServiceTest {

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private User testUser;
    private String testEmail;
    private String testIpAddress;
    private String testUserAgent;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testIpAddress = "192.168.1.1";
        testUserAgent = "Mozilla/5.0";

        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_USER");

        testUser = User.builder()
                .id(1L)
                .uuid("test-uuid-123")
                .email(testEmail)
                .username("testuser")
                .password("encodedPassword")
                .role(role)
                .failedLoginAttempts(0)
                .build();

        // Configurar valores de prueba
        ReflectionTestUtils.setField(loginAttemptService, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(loginAttemptService, "lockoutDurationMinutes", 30);
        ReflectionTestUtils.setField(loginAttemptService, "attemptWindowMinutes", 15);
    }

    @Test
    @DisplayName("Debe registrar login exitoso")
    void testRecordSuccessfulLogin() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(userRepository).resetFailedLoginAttempts(testUser.getId());

        // Act
        loginAttemptService.recordSuccessfulLogin(testEmail, testIpAddress, testUserAgent);

        // Assert
        verify(loginAttemptRepository).save(argThat(attempt -> 
            attempt.getEmail().equals(testEmail) &&
            attempt.getIpAddress().equals(testIpAddress) &&
            attempt.getUserAgent().equals(testUserAgent) &&
            attempt.isSuccess()
        ));
        verify(userRepository).resetFailedLoginAttempts(testUser.getId());
    }

    @Test
    @DisplayName("Debe registrar login fallido")
    void testRecordFailedLogin() {
        // Arrange
        String reason = "Credenciales inválidas";
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(userRepository).incrementFailedLoginAttempts(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        loginAttemptService.recordFailedLogin(testEmail, testIpAddress, testUserAgent, reason);

        // Assert
        verify(loginAttemptRepository).save(argThat(attempt -> 
            attempt.getEmail().equals(testEmail) &&
            attempt.getIpAddress().equals(testIpAddress) &&
            attempt.getUserAgent().equals(testUserAgent) &&
            !attempt.isSuccess() &&
            attempt.getFailureReason().equals(reason)
        ));
    }

    @Test
    @DisplayName("Debe verificar cuenta no bloqueada")
    void testIsAccountNotLocked() {
        // Arrange
        testUser.setAccountLockedUntil(null);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        boolean isLocked = loginAttemptService.isAccountLocked(testEmail);

        // Assert
        assertFalse(isLocked);
    }

    @Test
    @DisplayName("Debe verificar cuenta bloqueada")
    void testIsAccountLocked() {
        // Arrange
        testUser.setAccountLockedUntil(LocalDateTime.now().plusMinutes(30));
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        boolean isLocked = loginAttemptService.isAccountLocked(testEmail);

        // Assert
        assertTrue(isLocked);
    }

    @Test
    @DisplayName("Debe verificar cuenta desbloqueada después del tiempo")
    void testAccountUnlockedAfterTime() {
        // Arrange
        testUser.setAccountLockedUntil(LocalDateTime.now().minusMinutes(5));
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        boolean isLocked = loginAttemptService.isAccountLocked(testEmail);

        // Assert
        assertFalse(isLocked);
    }

    @Test
    @DisplayName("Debe retornar false si usuario no existe")
    void testIsAccountLockedUserNotExists() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act
        boolean isLocked = loginAttemptService.isAccountLocked(testEmail);

        // Assert
        assertFalse(isLocked);
    }

    @Test
    @DisplayName("Debe calcular minutos restantes de bloqueo")
    void testGetRemainingLockoutMinutes() {
        // Arrange
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15);
        testUser.setAccountLockedUntil(lockUntil);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        long remainingMinutes = loginAttemptService.getRemainingLockoutMinutes(testEmail);

        // Assert
        assertTrue(remainingMinutes > 0);
        assertTrue(remainingMinutes <= 15);
    }

    @Test
    @DisplayName("Debe retornar 0 si no hay bloqueo")
    void testGetRemainingLockoutMinutesNotLocked() {
        // Arrange
        testUser.setAccountLockedUntil(null);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        long remainingMinutes = loginAttemptService.getRemainingLockoutMinutes(testEmail);

        // Assert
        assertEquals(0, remainingMinutes);
    }

    @Test
    @DisplayName("No debe registrar login exitoso si usuario no existe")
    void testRecordSuccessfulLoginUserNotExists() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        loginAttemptService.recordSuccessfulLogin(testEmail, testIpAddress, testUserAgent);

        // Assert
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
        verify(userRepository, never()).resetFailedLoginAttempts(anyLong());
    }

    @Test
    @DisplayName("Debe incrementar intentos fallidos")
    void testIncrementFailedAttempts() {
        // Arrange
        testUser.setFailedLoginAttempts(2);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(userRepository).incrementFailedLoginAttempts(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        loginAttemptService.recordFailedLogin(testEmail, testIpAddress, testUserAgent, "Test reason");

        // Assert
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    @DisplayName("Debe manejar múltiples intentos fallidos sin bloquear")
    void testMultipleFailedAttemptsWithoutLocking() {
        // Arrange
        testUser.setFailedLoginAttempts(3);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(userRepository).incrementFailedLoginAttempts(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // Act
        loginAttemptService.recordFailedLogin(testEmail, testIpAddress, testUserAgent, "Test reason");

        // Assert
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
        // Verificar que el usuario no está bloqueado (menos de 5 intentos)
        assertNull(testUser.getAccountLockedUntil());
    }
}
