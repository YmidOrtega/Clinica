package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.entity.LoginAttempt;
import com.ClinicaDeYmid.auth_service.module.auth.repository.LoginAttemptRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;

    @Value("${auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${auth.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${auth.attempt-window-minutes:15}")
    private int attemptWindowMinutes;

    /**
     * Registra un intento de login exitoso
     */
    @Transactional
    public void recordSuccessfulLogin(String email, String ipAddress, String userAgent) {
        log.info("Registrando login exitoso para email: {}", email);

        LoginAttempt attempt = LoginAttempt.builder()
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .build();

        loginAttemptRepository.save(attempt);

        // Resetear contador de intentos fallidos
        userRepository.findByEmail(email).ifPresent(user -> {
            userRepository.resetFailedLoginAttempts(user.getId());
            log.debug("Contador de intentos fallidos reseteado para usuario: {}", email);
        });
    }

    /**
     * Registra un intento de login fallido
     */
    @Transactional
    public void recordFailedLogin(String email, String ipAddress, String userAgent, String reason) {
        log.warn("Registrando login fallido para email: {} desde IP: {}", email, ipAddress);

        LoginAttempt attempt = LoginAttempt.builder()
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(false)
                .failureReason(reason)
                .build();

        loginAttemptRepository.save(attempt);

        // Incrementar contador y verificar si debe bloquearse
        userRepository.findByEmail(email).ifPresent(this::handleFailedAttempt);
    }

    /**
     * Maneja un intento fallido, bloqueando la cuenta si es necesario
     */
    @Transactional
    protected void handleFailedAttempt(User user) {
        userRepository.incrementFailedLoginAttempts(user.getId());

        // Refrescar el usuario para obtener el contador actualizado
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();

        if (updatedUser.getFailedLoginAttempts() >= maxLoginAttempts) {
            lockAccount(updatedUser);
        } else {
            int remainingAttempts = maxLoginAttempts - updatedUser.getFailedLoginAttempts();
            log.warn("Usuario {} tiene {} intentos restantes antes de bloqueo",
                    user.getEmail(), remainingAttempts);
        }
    }

    /**
     * Bloquea una cuenta temporalmente
     */
    @Transactional
    protected void lockAccount(User user) {
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutDurationMinutes);

        userRepository.lockAccountUntil(user.getId(), lockUntil, StatusUser.SUSPENDED);

        log.warn("Cuenta bloqueada para usuario: {} hasta: {}", user.getEmail(), lockUntil);
    }

    /**
     * Verifica si una cuenta está bloqueada
     */
    public boolean isAccountLocked(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (user.getAccountLockedUntil() == null) {
                        return false;
                    }

                    boolean isLocked = LocalDateTime.now().isBefore(user.getAccountLockedUntil());

                    if (!isLocked && user.getStatus() == StatusUser.SUSPENDED) {
                        // Desbloquear automáticamente si el tiempo expiró
                        unlockAccount(user);
                    }

                    return isLocked;
                })
                .orElse(false);
    }

    /**
     * Desbloquea una cuenta manualmente
     */
    @Transactional
    public void unlockAccount(User user) {
        log.info("Desbloqueando cuenta para usuario: {}", user.getEmail());
        userRepository.resetFailedLoginAttempts(user.getId());

        // Actualizar estado si estaba suspendido por intentos fallidos
        if (user.getStatus() == StatusUser.SUSPENDED) {
            user.setStatus(StatusUser.ACTIVE);
            userRepository.save(user);
        }
    }

    /**
     * Obtiene el número de intentos fallidos recientes
     */
    public long getRecentFailedAttempts(String email) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(attemptWindowMinutes);
        return loginAttemptRepository.countFailedAttemptsSince(email, since);
    }

    /**
     * Obtiene el tiempo restante de bloqueo para una cuenta
     */
    public long getRemainingLockoutMinutes(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (user.getAccountLockedUntil() == null) {
                        return 0L;
                    }

                    LocalDateTime now = LocalDateTime.now();
                    if (now.isAfter(user.getAccountLockedUntil())) {
                        return 0L;
                    }

                    return java.time.Duration.between(now, user.getAccountLockedUntil()).toMinutes();
                })
                .orElse(0L);
    }

    /**
     * Desbloquea cuentas cuyo tiempo de bloqueo ha expirado (ejecutado periódicamente)
     */
    @Scheduled(fixedRate = 60000) // Cada minuto
    @Transactional
    public void unlockExpiredAccounts() {
        List<User> accountsToUnlock = userRepository.findAccountsToUnlock(
                LocalDateTime.now(),
                StatusUser.SUSPENDED
        );

        if (!accountsToUnlock.isEmpty()) {
            log.info("Desbloqueando {} cuentas cuyo tiempo de bloqueo expiró", accountsToUnlock.size());

            accountsToUnlock.forEach(this::unlockAccount);
        }
    }

    /**
     * Limpia intentos de login antiguos (ejecutado diariamente)
     */
    @Scheduled(cron = "${auth.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupOldLoginAttempts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        log.info("Ejecutando limpieza de intentos de login anteriores a: {}", cutoffDate);
        loginAttemptRepository.deleteByAttemptedAtBefore(cutoffDate);
    }
}