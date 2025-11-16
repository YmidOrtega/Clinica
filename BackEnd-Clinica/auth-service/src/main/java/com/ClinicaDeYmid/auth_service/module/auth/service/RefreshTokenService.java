package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.auth.repository.RefreshTokenRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiration:604800}")
    private long refreshTokenExpirationSeconds;

    @Value("${auth.max-active-sessions:5}")
    private int maxActiveSessions;

    /**
     * Crea y persiste un nuevo refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(
            String token,
            User user,
            String ipAddress,
            String userAgent
    ) {
        log.debug("Creando refresh token para usuario: {}", user.getEmail());

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);

        // Limpiar tokens antiguos si excede el máximo de sesiones
        cleanupExcessiveSessions(user);

        return saved;
    }

    /**
     * Busca un refresh token válido
     */
    public Optional<RefreshToken> findValidToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(RefreshToken::isValid);
    }

    /**
     * Revoca un refresh token específico
     */
    @Transactional
    public void revokeToken(String token) {
        log.info("Revocando refresh token");

        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);
        });
    }

    /**
     * Revoca todos los tokens de un usuario (logout from all devices)
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        log.info("Revocando todos los tokens del usuario: {}", user.getEmail());

        int revokedCount = refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());

        log.info("Se revocaron {} tokens para el usuario: {}", revokedCount, user.getEmail());
    }

    /**
     * Implementa refresh token rotation
     */
    @Transactional
    public RefreshToken rotateToken(
            RefreshToken oldToken,
            String newTokenValue,
            String ipAddress,
            String userAgent
    ) {
        log.debug("Rotando refresh token para usuario: {}", oldToken.getUser().getEmail());

        // Marcar el token viejo como reemplazado
        oldToken.setReplacedByToken(newTokenValue);
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        // Crear el nuevo token
        return createRefreshToken(newTokenValue, oldToken.getUser(), ipAddress, userAgent);
    }

    /**
     * Obtiene todos los tokens válidos de un usuario
     */
    public List<RefreshToken> getUserActiveSessions(User user) {
        return refreshTokenRepository.findValidTokensByUser(user, LocalDateTime.now());
    }

    /**
     * Verifica si un usuario ha excedido el límite de sesiones activas
     */
    public boolean hasExceededMaxSessions(User user) {
        long activeSessionsCount = refreshTokenRepository
                .countValidTokensByUser(user, LocalDateTime.now());

        return activeSessionsCount >= maxActiveSessions;
    }

    /**
     * Limpia tokens antiguos si el usuario excede el máximo de sesiones
     */
    @Transactional
    protected void cleanupExcessiveSessions(User user) {
        List<RefreshToken> activeSessions = getUserActiveSessions(user);

        if (activeSessions.size() > maxActiveSessions) {
            log.info("Usuario {} excedió el límite de sesiones activas. Limpiando...",
                    user.getEmail());

            // Revocar las sesiones más antiguas
            // La lista viene ordenada por createdAt DESC, entonces saltamos los más recientes
            int tokensToRevoke = activeSessions.size() - maxActiveSessions;

            activeSessions.stream()
                    .skip(maxActiveSessions)  // Saltamos los tokens más recientes
                    .forEach(token -> {
                        token.revoke();
                        refreshTokenRepository.save(token);
                    });

            log.debug("Se revocaron {} sesiones antiguas", tokensToRevoke);
        }
    }

    /**
     * Limpia tokens expirados de la base de datos (ejecutado diariamente)
     */
    @Scheduled(cron = "${auth.cleanup-cron:0 0 2 * * *}") // 2 AM por defecto
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Ejecutando limpieza de refresh tokens expirados");

        int deletedCount = refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());

        if (deletedCount > 0) {
            log.info("Se eliminaron {} refresh tokens expirados", deletedCount);
        }
    }

    /**
     * Obtiene información de sesión por token
     */
    public Optional<SessionInfo> getSessionInfo(String token) {
        return refreshTokenRepository.findByToken(token)
                .map(rt -> new SessionInfo(
                        rt.getIpAddress(),
                        rt.getUserAgent(),
                        rt.getCreatedAt(),
                        rt.getExpiresAt(),
                        rt.isRevoked()
                ));
    }

    public record SessionInfo(
            String ipAddress,
            String userAgent,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            boolean revoked
    ) {}
}