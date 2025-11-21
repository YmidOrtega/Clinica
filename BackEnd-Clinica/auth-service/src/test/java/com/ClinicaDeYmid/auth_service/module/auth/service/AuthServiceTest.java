package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.AccountLockedException;
import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.auth.dto.LoginRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PublicKeyResponse;
import com.ClinicaDeYmid.auth_service.module.auth.dto.RefreshTokenRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenHelper tokenHelper;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PasswordPolicyService passwordPolicyService;

    @InjectMocks
    private AuthService authService;

    private LoginRequest loginRequest;
    private User user;
    private TokenPair tokenPair;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest("test@example.com", "password123");
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setRequirePasswordChange(false);

        tokenPair = new TokenPair("accessToken", "refreshToken");
        authentication = mock(Authentication.class);
    }

    @Test
    @DisplayName("Should return TokenPair on successful login")
    void login_success() {
        when(loginAttemptService.isAccountLocked(loginRequest.email())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordPolicyService.isPasswordExpired(user)).thenReturn(false);
        when(tokenService.generateTokenPair(user, "127.0.0.1", "test-agent")).thenReturn(tokenPair);

        TokenPair result = authService.login(loginRequest, "127.0.0.1", "test-agent");

        assertNotNull(result);
        assertEquals(tokenPair.accessToken(), result.accessToken());
        assertEquals(tokenPair.refreshToken(), result.refreshToken());

        verify(loginAttemptService, times(1)).recordSuccessfulLogin(user.getEmail(), "127.0.0.1", "test-agent");
        verify(auditLogService, times(1)).logLoginSuccess(user, "127.0.0.1", "test-agent");
    }

    @Test
    @DisplayName("Should throw BadCredentialsException on invalid credentials")
    void login_invalidCredentials() {
        when(loginAttemptService.isAccountLocked(loginRequest.email())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> {
            authService.login(loginRequest, "127.0.0.1", "test-agent");
        });

        verify(loginAttemptService, times(1)).recordFailedLogin(loginRequest.email(), "127.0.0.1", "test-agent", "Credenciales inválidas");
        verify(auditLogService, times(1)).logLoginFailed(loginRequest.email(), "Credenciales inválidas", "127.0.0.1", "test-agent");
    }

    @Test
    @DisplayName("Should throw AccountLockedException if account is locked")
    void login_accountLocked() {
        when(loginAttemptService.isAccountLocked(loginRequest.email())).thenReturn(true);
        when(loginAttemptService.getRemainingLockoutMinutes(loginRequest.email())).thenReturn(15L);

        AccountLockedException exception = assertThrows(AccountLockedException.class, () -> {
            authService.login(loginRequest, "127.0.0.1", "test-agent");
        });

        assertEquals("Cuenta bloqueada. Intente nuevamente en 15 minutos", exception.getMessage());
        verify(loginAttemptService, times(1)).recordFailedLogin(loginRequest.email(), "127.0.0.1", "test-agent", "Cuenta bloqueada");
        verify(auditLogService, times(1)).logLoginFailed(loginRequest.email(), "Cuenta bloqueada", "127.0.0.1", "test-agent");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException if password expired")
    void login_passwordExpired() {
        when(loginAttemptService.isAccountLocked(loginRequest.email())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordPolicyService.isPasswordExpired(user)).thenReturn(true);

        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> {
            authService.login(loginRequest, "127.0.0.1", "test-agent");
        });

        assertEquals("Tu contraseña ha expirado. Debes cambiarla.", exception.getMessage());
        verify(loginAttemptService, never()).recordSuccessfulLogin(anyString(), anyString(), anyString());
        verify(auditLogService, never()).logLoginSuccess(any(User.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException if password change is required")
    void login_passwordChangeRequired() {
        user.setRequirePasswordChange(true);
        when(loginAttemptService.isAccountLocked(loginRequest.email())).thenReturn(false);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordPolicyService.isPasswordExpired(user)).thenReturn(false);

        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> {
            authService.login(loginRequest, "127.0.0.1", "test-agent");
        });

        assertEquals("Debes cambiar tu contraseña antes de continuar.", exception.getMessage());
        verify(loginAttemptService, never()).recordSuccessfulLogin(anyString(), anyString(), anyString());
        verify(auditLogService, never()).logLoginSuccess(any(User.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Should refresh token successfully")
    void refresh_success() {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("refreshToken");
        TokenPair newTokenPair = new TokenPair("newAccessToken", "newRefreshToken");
        String userUuid = "user-uuid";

        when(tokenService.getSubject(refreshRequest.refreshToken())).thenReturn(userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(tokenService.refreshTokens(refreshRequest.refreshToken(), user, "127.0.0.1", "test-agent")).thenReturn(newTokenPair);

        TokenPair result = authService.refresh(refreshRequest, "127.0.0.1", "test-agent");

        assertNotNull(result);
        assertEquals(newTokenPair, result);
        verify(auditLogService, times(1)).logRefreshTokenUsed(user, "127.0.0.1");
    }

    @Test
    @DisplayName("Should throw UserNotFoundException on refresh if user not found")
    void refresh_userNotFound() {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("refreshToken");
        String userUuid = "user-uuid";

        when(tokenService.getSubject(refreshRequest.refreshToken())).thenReturn(userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            authService.refresh(refreshRequest, "127.0.0.1", "test-agent");
        });
    }

    @Test
    @DisplayName("Should logout successfully")
    void logout_success() {
        String authHeader = "Bearer accessToken";
        String token = "accessToken";
        String userUuid = "user-uuid";

        when(tokenHelper.extractToken(authHeader)).thenReturn(token);
        doNothing().when(tokenService).validateAccessToken(token);
        when(tokenService.getSubject(token)).thenReturn(userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(tokenService.getExpirationInSeconds(token)).thenReturn(3600L);

        authService.logout(authHeader, "127.0.0.1", "test-agent");

        verify(tokenBlacklistService, times(1)).blacklistToken(token, 3600L);
        verify(auditLogService, times(1)).logLogout(user, "127.0.0.1", "test-agent");
    }

    @Test
    @DisplayName("Should logout from all devices successfully")
    void logoutFromAllDevices_success() {
        String authHeader = "Bearer accessToken";
        String token = "accessToken";
        String userUuid = "user-uuid";

        when(tokenHelper.extractToken(authHeader)).thenReturn(token);
        doNothing().when(tokenService).validateAccessToken(token);
        when(tokenService.getSubject(token)).thenReturn(userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(tokenService.getExpirationInSeconds(token)).thenReturn(3600L);

        authService.logoutFromAllDevices(authHeader);

        verify(refreshTokenService, times(1)).revokeAllUserTokens(user);
        verify(tokenBlacklistService, times(1)).blacklistToken(token, 3600L);
        verify(auditLogService, times(1)).logAction(
                user,
                com.ClinicaDeYmid.auth_service.module.auth.enums.AuditAction.LOGOUT,
                "Logout de todos los dispositivos",
                null,
                null
        );
    }

    @Test
    @DisplayName("Should return public key successfully")
    void getPublicKey_success() throws Exception {
        // Create a dummy RSA public key
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair pair = keyGen.generateKeyPair();
        java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey) pair.getPublic();
        String encodedKey = java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded());

        when(tokenService.getPublicKey()).thenReturn(publicKey);

        PublicKeyResponse response = authService.getPublicKey();

        assertNotNull(response);
        assertEquals(encodedKey, response.publicKey());
        assertEquals("RS256", response.algorithm());
        assertEquals("RSA", response.keyType());
    }
}
