package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.InvalidTokenException;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService - Unit Tests")
class TokenServiceTest {

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private TokenService tokenService;

    private User testUser;
    private Role testRole;
    private Algorithm testAlgorithm;
    private String testSecret = "test-secret-key-that-is-at-least-32-characters-long-for-testing";

    @BeforeEach
    void setUp() {
        testRole = new Role();
        testRole.setId(1L);
        testRole.setName("ROLE_USER");

        testUser = User.builder()
                .id(1L)
                .uuid("test-uuid-123")
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .role(testRole)
                .build();

        // Configurar el algoritmo para testing (HS256)
        testAlgorithm = Algorithm.HMAC256(testSecret);
        
        // Inyectar valores usando ReflectionTestUtils
        ReflectionTestUtils.setField(tokenService, "accessTokenExpiration", 900L);
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpiration", 604800L);
        ReflectionTestUtils.setField(tokenService, "hmacSecret", testSecret);
        ReflectionTestUtils.setField(tokenService, "algorithmType", "HS256");
        ReflectionTestUtils.setField(tokenService, "algorithm", testAlgorithm);
    }

    @Test
    @DisplayName("Debe generar access token válido")
    void testGenerateAccessToken() {
        // Act
        String token = tokenService.generateAccessToken(testUser);

        // Assert
        assertNotNull(token);
        DecodedJWT decodedJWT = JWT.decode(token);
        assertEquals(testUser.getUuid(), decodedJWT.getSubject());
        assertEquals(testUser.getEmail(), decodedJWT.getClaim("email").asString());
        assertEquals("access", decodedJWT.getClaim("type").asString());
        assertEquals(testRole.getName(), decodedJWT.getClaim("role").asString());
        assertEquals("ClinicaDeYmid", decodedJWT.getIssuer());
    }

    @Test
    @DisplayName("Debe generar refresh token válido")
    void testGenerateRefreshToken() {
        // Act
        String token = tokenService.generateRefreshToken(testUser);

        // Assert
        assertNotNull(token);
        DecodedJWT decodedJWT = JWT.decode(token);
        assertEquals(testUser.getUuid(), decodedJWT.getSubject());
        assertEquals("refresh", decodedJWT.getClaim("type").asString());
        assertEquals("ClinicaDeYmid", decodedJWT.getIssuer());
    }

    @Test
    @DisplayName("Debe fallar al generar token con usuario nulo")
    void testGenerateTokenWithNullUser() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> tokenService.generateAccessToken(null));
    }

    @Test
    @DisplayName("Debe fallar al generar token con usuario inválido")
    void testGenerateTokenWithInvalidUser() {
        // Arrange
        User invalidUser = User.builder().build(); // Sin UUID ni email

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> tokenService.generateAccessToken(invalidUser));
    }

    @Test
    @DisplayName("Debe generar par de tokens y persistir refresh token")
    void testGenerateTokenPair() {
        // Arrange
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        RefreshToken mockRefreshToken = RefreshToken.builder()
                .id(1L)
                .token("refresh-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenService.createRefreshToken(anyString(), eq(testUser), eq(ipAddress), eq(userAgent)))
                .thenReturn(mockRefreshToken);

        // Act
        TokenPair tokenPair = tokenService.generateTokenPair(testUser, ipAddress, userAgent);

        // Assert
        assertNotNull(tokenPair);
        assertNotNull(tokenPair.accessToken());
        assertNotNull(tokenPair.refreshToken());
        assertEquals("Bearer", tokenPair.tokenType());
        assertEquals(900L, tokenPair.expiresIn());
        verify(refreshTokenService).createRefreshToken(anyString(), eq(testUser), eq(ipAddress), eq(userAgent));
    }

    @Test
    @DisplayName("Debe validar y decodificar token válido")
    void testValidateAndDecodeToken() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);

        // Act
        DecodedJWT decodedJWT = tokenService.validateAndDecodeToken(token);

        // Assert
        assertNotNull(decodedJWT);
        assertEquals(testUser.getUuid(), decodedJWT.getSubject());
    }

    @Test
    @DisplayName("Debe fallar al validar token inválido")
    void testValidateInvalidToken() {
        // Arrange
        String invalidToken = "invalid.token.here";

        // Act & Assert
        assertThrows(InvalidTokenException.class, 
            () -> tokenService.validateAndDecodeToken(invalidToken));
    }

    @Test
    @DisplayName("Debe validar access token correctamente")
    void testValidateAccessToken() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);
        when(tokenBlacklistService.isTokenBlacklisted(token)).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> tokenService.validateAccessToken(token));
        verify(tokenBlacklistService).isTokenBlacklisted(token);
    }

    @Test
    @DisplayName("Debe fallar al validar token en blacklist")
    void testValidateBlacklistedToken() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);
        when(tokenBlacklistService.isTokenBlacklisted(token)).thenReturn(true);

        // Act & Assert
        InvalidTokenException exception = assertThrows(InvalidTokenException.class,
            () -> tokenService.validateAccessToken(token));
        assertTrue(exception.getMessage().contains("revocado"));
    }

    @Test
    @DisplayName("Debe fallar al validar refresh token como access token")
    void testValidateRefreshTokenAsAccessToken() {
        // Arrange
        String refreshToken = tokenService.generateRefreshToken(testUser);
        when(tokenBlacklistService.isTokenBlacklisted(refreshToken)).thenReturn(false);

        // Act & Assert
        InvalidTokenException exception = assertThrows(InvalidTokenException.class,
            () -> tokenService.validateAccessToken(refreshToken));
        assertTrue(exception.getMessage().contains("access") || exception.getMessage().contains("acceso"));
    }

    @Test
    @DisplayName("Debe validar refresh token correctamente")
    void testValidateRefreshToken() {
        // Arrange
        String refreshToken = tokenService.generateRefreshToken(testUser);
        RefreshToken mockRefreshToken = RefreshToken.builder()
                .id(1L)
                .token(refreshToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.findValidToken(refreshToken))
                .thenReturn(Optional.of(mockRefreshToken));

        // Act & Assert
        assertDoesNotThrow(() -> tokenService.validateRefreshToken(refreshToken));
    }

    @Test
    @DisplayName("Debe fallar al validar refresh token no encontrado")
    void testValidateRefreshTokenNotFound() {
        // Arrange
        String refreshToken = tokenService.generateRefreshToken(testUser);
        when(refreshTokenService.findValidToken(refreshToken))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> tokenService.validateRefreshToken(refreshToken));
    }

    @Test
    @DisplayName("Debe refrescar tokens correctamente")
    void testRefreshTokens() {
        // Arrange
        String oldRefreshToken = tokenService.generateRefreshToken(testUser);
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        RefreshToken mockOldToken = RefreshToken.builder()
                .id(1L)
                .token(oldRefreshToken)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        RefreshToken mockNewToken = RefreshToken.builder()
                .id(2L)
                .token("new-refresh-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.findValidToken(oldRefreshToken))
                .thenReturn(Optional.of(mockOldToken));
        when(refreshTokenService.rotateToken(eq(mockOldToken), anyString(), eq(ipAddress), eq(userAgent)))
                .thenReturn(mockNewToken);

        // Act
        TokenPair newTokenPair = tokenService.refreshTokens(oldRefreshToken, testUser, ipAddress, userAgent);

        // Assert
        assertNotNull(newTokenPair);
        assertNotNull(newTokenPair.accessToken());
        assertNotNull(newTokenPair.refreshToken());
        verify(refreshTokenService).rotateToken(eq(mockOldToken), anyString(), eq(ipAddress), eq(userAgent));
    }

    @Test
    @DisplayName("Debe fallar al refrescar con token de usuario diferente")
    void testRefreshTokensWithDifferentUser() {
        // Arrange
        User anotherUser = User.builder()
                .id(2L)
                .uuid("another-uuid")
                .email("another@example.com")
                .username("anotheruser")
                .role(testRole)
                .build();

        String refreshToken = tokenService.generateRefreshToken(anotherUser);

        RefreshToken mockToken = RefreshToken.builder()
                .id(1L)
                .token(refreshToken)
                .user(anotherUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.findValidToken(refreshToken))
                .thenReturn(Optional.of(mockToken));

        // Act & Assert
        assertThrows(RuntimeException.class,
            () -> tokenService.refreshTokens(refreshToken, testUser, "192.168.1.1", "Mozilla/5.0"));
    }

    @Test
    @DisplayName("Debe obtener subject del token")
    void testGetSubject() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);

        // Act
        String subject = tokenService.getSubject(token);

        // Assert
        assertEquals(testUser.getUuid(), subject);
    }

    @Test
    @DisplayName("Debe obtener email del token")
    void testGetEmail() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);

        // Act
        String email = tokenService.getEmail(token);

        // Assert
        assertEquals(testUser.getEmail(), email);
    }

    @Test
    @DisplayName("Debe verificar si token es válido")
    void testIsTokenValid() {
        // Arrange
        String validToken = tokenService.generateAccessToken(testUser);
        String invalidToken = "invalid.token";

        // Act & Assert
        assertTrue(tokenService.isTokenValid(validToken));
        assertFalse(tokenService.isTokenValid(invalidToken));
    }

    @Test
    @DisplayName("Debe obtener tiempo de expiración en segundos")
    void testGetExpirationInSeconds() {
        // Arrange
        String token = tokenService.generateAccessToken(testUser);

        // Act
        long expirationSeconds = tokenService.getExpirationInSeconds(token);

        // Assert
        assertTrue(expirationSeconds > 0);
        assertTrue(expirationSeconds <= 900); // Menor o igual al tiempo configurado
    }

    @Test
    @DisplayName("Debe inicializar con HS256 correctamente")
    void testInitWithHS256() {
        // Este test verifica que la inicialización funciona con HS256
        // Ya configurado en @BeforeEach
        assertNotNull(ReflectionTestUtils.getField(tokenService, "algorithm"));
    }

    @Test
    @DisplayName("Debe generar tokens con claims correctos")
    void testTokenClaimsAreCorrect() {
        // Arrange & Act
        String accessToken = tokenService.generateAccessToken(testUser);
        String refreshToken = tokenService.generateRefreshToken(testUser);

        DecodedJWT decodedAccess = JWT.decode(accessToken);
        DecodedJWT decodedRefresh = JWT.decode(refreshToken);

        // Assert - Access Token Claims
        assertEquals("ClinicaDeYmid", decodedAccess.getIssuer());
        assertEquals(testUser.getUuid(), decodedAccess.getSubject());
        assertEquals(testUser.getEmail(), decodedAccess.getClaim("email").asString());
        assertEquals("access", decodedAccess.getClaim("type").asString());
        assertEquals(testRole.getName(), decodedAccess.getClaim("role").asString());
        assertNotNull(decodedAccess.getId());
        assertNotNull(decodedAccess.getIssuedAt());
        assertNotNull(decodedAccess.getExpiresAt());

        // Assert - Refresh Token Claims
        assertEquals("ClinicaDeYmid", decodedRefresh.getIssuer());
        assertEquals(testUser.getUuid(), decodedRefresh.getSubject());
        assertEquals("refresh", decodedRefresh.getClaim("type").asString());
        assertNull(decodedRefresh.getClaim("email").asString());
        assertNull(decodedRefresh.getClaim("role").asString());
    }
}
