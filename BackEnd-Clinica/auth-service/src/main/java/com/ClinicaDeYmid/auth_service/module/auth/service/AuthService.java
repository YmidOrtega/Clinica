package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.AccountLockedException;
import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.auth.dto.LoginRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PublicKeyResponse;
import com.ClinicaDeYmid.auth_service.module.auth.dto.RefreshTokenRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TokenHelper tokenHelper;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final PasswordPolicyService passwordPolicyService;

    @Transactional
    public TokenPair login(LoginRequest loginRequest, String ipAddress, String userAgent) {
        log.info("Intento de login para email: {} desde IP: {}", loginRequest.email(), ipAddress);

        // Verificar si la cuenta está bloqueada
        if (loginAttemptService.isAccountLocked(loginRequest.email())) {
            long remainingMinutes = loginAttemptService.getRemainingLockoutMinutes(loginRequest.email());

            loginAttemptService.recordFailedLogin(
                    loginRequest.email(),
                    ipAddress,
                    userAgent,
                    "Cuenta bloqueada"
            );

            auditLogService.logLoginFailed(
                    loginRequest.email(),
                    "Cuenta bloqueada",
                    ipAddress,
                    userAgent
            );

            throw new AccountLockedException(
                    String.format("Cuenta bloqueada. Intente nuevamente en %d minutos", remainingMinutes)
            );
        }

        try {
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    loginRequest.email(),
                    loginRequest.password()
            );

            Authentication authentication = authenticationManager.authenticate(authToken);
            User user = (User) authentication.getPrincipal();

            // Verificar si la contraseña expiró
            if (passwordPolicyService.isPasswordExpired(user)) {
                log.warn("Contraseña expirada para usuario: {}", user.getEmail());
                throw new BadCredentialsException("Tu contraseña ha expirado. Debes cambiarla.");
            }

            // Verificar si requiere cambio de contraseña
            if (user.isRequirePasswordChange()) {
                log.warn("Usuario {} requiere cambio de contraseña", user.getEmail());
                throw new BadCredentialsException("Debes cambiar tu contraseña antes de continuar.");
            }

            // Login exitoso
            loginAttemptService.recordSuccessfulLogin(user.getEmail(), ipAddress, userAgent);
            auditLogService.logLoginSuccess(user, ipAddress, userAgent);

            TokenPair tokenPair = tokenService.generateTokenPair(user, ipAddress, userAgent);

            log.info("Login exitoso para usuario: {}", user.getEmail());
            return tokenPair;

        } catch (AuthenticationException e) {
            // Si es una de nuestras excepciones específicas de políticas de contraseñas, la re-lanzamos.
            if (e instanceof BadCredentialsException &&
                (e.getMessage().equals("Tu contraseña ha expirado. Debes cambiarla.") ||
                 e.getMessage().equals("Debes cambiar tu contraseña antes de continuar."))) {
                throw e;
            }

            log.warn("Login fallido para email: {} - Razón: {}", loginRequest.email(), e.getMessage());

            loginAttemptService.recordFailedLogin(
                    loginRequest.email(),
                    ipAddress,
                    userAgent,
                    "Credenciales inválidas"
            );

            auditLogService.logLoginFailed(
                    loginRequest.email(),
                    "Credenciales inválidas",
                    ipAddress,
                    userAgent
            );

            throw new BadCredentialsException("Credenciales inválidas");
        }
    }

    @Transactional
    public TokenPair refresh(RefreshTokenRequest refreshRequest, String ipAddress, String userAgent) {
        log.debug("Intento de refresh token desde IP: {}", ipAddress);

        String userUuid = tokenService.getSubject(refreshRequest.refreshToken());
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con UUID: " + userUuid));

        TokenPair newTokenPair = tokenService.refreshTokens(
                refreshRequest.refreshToken(),
                user,
                ipAddress,
                userAgent
        );

        auditLogService.logRefreshTokenUsed(user, ipAddress);

        log.debug("Tokens renovados exitosamente para usuario: {}", user.getEmail());
        return newTokenPair;
    }

    @Transactional
    public void logout(String authHeader, String ipAddress, String userAgent) {
        String token = tokenHelper.extractToken(authHeader);
        tokenService.validateAccessToken(token);

        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con UUID: " + userUuid));

        // Agregar token a blacklist
        long expiration = tokenService.getExpirationInSeconds(token);
        tokenBlacklistService.blacklistToken(token, expiration);

        // Auditar
        auditLogService.logLogout(user, ipAddress, userAgent);

        log.info("Logout exitoso para usuario: {}", user.getEmail());
    }

    @Transactional
    public void logoutFromAllDevices(String authHeader) {
        String token = tokenHelper.extractToken(authHeader);
        tokenService.validateAccessToken(token);

        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con UUID: " + userUuid));

        // Revocar todos los refresh tokens
        refreshTokenService.revokeAllUserTokens(user);

        // Agregar el access token actual a blacklist
        long expiration = tokenService.getExpirationInSeconds(token);
        tokenBlacklistService.blacklistToken(token, expiration);

        auditLogService.logAction(
                user,
                com.ClinicaDeYmid.auth_service.module.auth.enums.AuditAction.LOGOUT,
                "Logout de todos los dispositivos",
                null,
                null
        );

        log.info("Logout de todos los dispositivos para usuario: {}", user.getEmail());
    }

    public PublicKeyResponse getPublicKey() {
        RSAPublicKey publicKey = tokenService.getPublicKey();
        String encodedKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        log.debug("Clave pública obtenida exitosamente");

        return new PublicKeyResponse(
                encodedKey,
                "RS256",
                "RSA"
        );
    }
}