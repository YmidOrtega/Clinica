package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.auth.repository.RefreshTokenRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
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
@DisplayName("RefreshTokenService - Unit Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;
    private String testToken;
    private String testIpAddress;
    private String testUserAgent;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_USER");

        testUser = User.builder()
                .id(1L)
                .uuid("test-uuid-123")
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .role(role)
                .build();

        testToken = "test-refresh-token-123";
        testIpAddress = "192.168.1.1";
        testUserAgent = "Mozilla/5.0";

        // Configurar valores de prueba
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationSeconds", 604800L);
        ReflectionTestUtils.setField(refreshTokenService, "maxActiveSessions", 5);
    }

    @Test
    @DisplayName("Debe crear refresh token correctamente")
    void testCreateRefreshToken() {
        // Arrange
        RefreshToken savedToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .ipAddress(testIpAddress)
                .userAgent(testUserAgent)
                .revoked(false)
                .build();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);
        // No need to mock countByUserAndRevokedFalseAndExpiresAtAfter as cleanupExcessiveSessions is internal

        // Act
        RefreshToken result = refreshTokenService.createRefreshToken(testToken, testUser, testIpAddress, testUserAgent);

        // Assert
        assertNotNull(result);
        assertEquals(testToken, result.getToken());
        assertEquals(testUser, result.getUser());
        assertEquals(testIpAddress, result.getIpAddress());
        assertEquals(testUserAgent, result.getUserAgent());
        assertFalse(result.isRevoked());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Debe encontrar token válido")
    void testFindValidToken() {
        // Arrange
        RefreshToken validToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(refreshTokenRepository.findByToken(testToken)).thenReturn(Optional.of(validToken));

        // Act
        Optional<RefreshToken> result = refreshTokenService.findValidToken(testToken);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testToken, result.get().getToken());
        assertTrue(result.get().isValid());
    }

    @Test
    @DisplayName("No debe encontrar token revocado")
    void testFindValidTokenRevoked() {
        // Arrange
        RefreshToken revokedToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .revokedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(refreshTokenRepository.findByToken(testToken)).thenReturn(Optional.of(revokedToken));

        // Act
        Optional<RefreshToken> result = refreshTokenService.findValidToken(testToken);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("No debe encontrar token expirado")
    void testFindValidTokenExpired() {
        // Arrange
        RefreshToken expiredToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .createdAt(LocalDateTime.now().minusDays(8))
                .build();

        when(refreshTokenRepository.findByToken(testToken)).thenReturn(Optional.of(expiredToken));

        // Act
        Optional<RefreshToken> result = refreshTokenService.findValidToken(testToken);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Debe revocar token correctamente")
    void testRevokeToken() {
        // Arrange
        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(refreshTokenRepository.findByToken(testToken)).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(token);

        // Act
        refreshTokenService.revokeToken(testToken);

        // Assert
        verify(refreshTokenRepository).findByToken(testToken);
        verify(refreshTokenRepository).save(argThat(t -> t.isRevoked() && t.getRevokedAt() != null));
    }

    @Test
    @DisplayName("No debe fallar al revocar token inexistente")
    void testRevokeNonExistentToken() {
        // Arrange
        when(refreshTokenRepository.findByToken(testToken)).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> refreshTokenService.revokeToken(testToken));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rotar token correctamente")
    void testRotateToken() {
        // Arrange
        RefreshToken oldToken = RefreshToken.builder()
                .id(1L)
                .token("old-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        String newTokenString = "new-token";
        RefreshToken newToken = RefreshToken.builder()
                .id(2L)
                .token(newTokenString)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .ipAddress(testIpAddress)
                .userAgent(testUserAgent)
                .revoked(false)
                .build();

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(oldToken, newToken);
        // No need to mock countByUserAndRevokedFalseAndExpiresAtAfter

        // Act
        RefreshToken result = refreshTokenService.rotateToken(oldToken, newTokenString, testIpAddress, testUserAgent);

        // Assert
        assertNotNull(result);
        assertEquals(newTokenString, result.getToken());
        assertTrue(oldToken.isRevoked());
        assertEquals(newTokenString, oldToken.getReplacedByToken());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Debe revocar todos los tokens del usuario")
    void testRevokeAllUserTokens() {
        // Arrange
        RefreshToken token1 = RefreshToken.builder()
                .id(1L)
                .token("token-1")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .id(2L)
                .token("token-2")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Debe validar token correctamente")
    void testTokenIsValid() {
        // Arrange
        RefreshToken validToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        // Act & Assert
        assertTrue(validToken.isValid());
    }

    @Test
    @DisplayName("Token revocado no debe ser válido")
    void testRevokedTokenIsNotValid() {
        // Arrange
        RefreshToken revokedToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .revokedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        // Act & Assert
        assertFalse(revokedToken.isValid());
    }

    @Test
    @DisplayName("Token expirado no debe ser válido")
    void testExpiredTokenIsNotValid() {
        // Arrange
        RefreshToken expiredToken = RefreshToken.builder()
                .id(1L)
                .token(testToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .createdAt(LocalDateTime.now().minusDays(8))
                .build();

        // Act & Assert
        assertFalse(expiredToken.isValid());
    }
}
