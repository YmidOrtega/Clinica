package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.entity.AuditLog;
import com.ClinicaDeYmid.auth_service.module.auth.enums.AuditAction;
import com.ClinicaDeYmid.auth_service.module.auth.repository.AuditLogRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Registra una acción de auditoría (asíncrono para no afectar performance)
     */
    @Async
    @Transactional
    public void logAction(
            User user,
            AuditAction action,
            String details,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);

        log.debug("Acción auditada: {} para usuario: {}", action,
                user != null ? user.getEmail() : "N/A");
    }

    /**
     * Registra una acción sin usuario asociado (asíncrono para no afectar performance)
     */
    @Async
    @Transactional
    public void logActionWithoutUser(
            String email,
            AuditAction action,
            String details,
            String ipAddress,
            String userAgent
    ) {
        AuditLog auditLog = AuditLog.builder()
                .userEmail(email)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);

        log.debug("Acción auditada: {} para email: {}", action, email);
    }

    public void logLoginSuccess(User user, String ipAddress, String userAgent) {
        logAction(user, AuditAction.LOGIN_SUCCESS, "Login exitoso", ipAddress, userAgent);
    }

    public void logLoginFailed(String email, String reason, String ipAddress, String userAgent) {
        logActionWithoutUser(email, AuditAction.LOGIN_FAILED, reason, ipAddress, userAgent);
    }

    public void logLogout(User user, String ipAddress, String userAgent) {
        logAction(user, AuditAction.LOGOUT, "Logout exitoso", ipAddress, userAgent);
    }

    public void logPasswordChanged(User user) {
        logAction(user, AuditAction.PASSWORD_CHANGED, "Contraseña cambiada por el usuario", null, null);
    }

    public void logPasswordResetRequested(User user) {
        logAction(user, AuditAction.PASSWORD_RESET_REQUESTED, "Token de reseteo solicitado", null, null);
    }

    public void logPasswordResetCompleted(User user) {
        logAction(user, AuditAction.PASSWORD_RESET_COMPLETED, "Contraseña reseteada exitosamente", null, null);
    }

    public void logAccountLocked(User user, String reason) {
        logAction(user, AuditAction.ACCOUNT_LOCKED, reason, null, null);
    }

    public void logAccountUnlocked(User user) {
        logAction(user, AuditAction.ACCOUNT_UNLOCKED, "Cuenta desbloqueada", null, null);
    }

    public void logUserCreated(User user, User createdBy) {
        logAction(createdBy, AuditAction.USER_CREATED,
                "Usuario creado: " + user.getEmail(), null, null);
    }

    public void logUserUpdated(User user, User updatedBy) {
        logAction(updatedBy, AuditAction.USER_UPDATED,
                "Usuario actualizado: " + user.getEmail(), null, null);
    }

    public void logUserDeleted(User user, User deletedBy) {
        logAction(deletedBy, AuditAction.USER_DELETED,
                "Usuario eliminado: " + user.getEmail(), null, null);
    }

    public void logRefreshTokenUsed(User user, String ipAddress) {
        logAction(user, AuditAction.REFRESH_TOKEN_USED, "Refresh token utilizado", ipAddress, null);
    }

    public void logTokenRevoked(User user) {
        logAction(user, AuditAction.TOKEN_REVOKED, "Token revocado", null, null);
    }

    public void logUnauthorizedAccess(String email, String details, String ipAddress, String userAgent) {
        logActionWithoutUser(email, AuditAction.UNAUTHORIZED_ACCESS, details, ipAddress, userAgent);
    }

    /**
     * Obtiene logs por usuario
     */
    public Page<AuditLog> getUserLogs(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Obtiene logs por email
     */
    public Page<AuditLog> getUserLogsByEmail(String email, Pageable pageable) {
        return auditLogRepository.findByUserEmailOrderByCreatedAtDesc(email, pageable);
    }

    /**
     * Obtiene logs por acción
     */
    public Page<AuditLog> getLogsByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    /**
     * Obtiene logs por rango de fechas
     */
    public Page<AuditLog> getLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return auditLogRepository.findByDateRange(startDate, endDate, pageable);
    }

    /**
     * Busca actividad sospechosa
     */
    public List<Object[]> findSuspiciousActivity(int threshold, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<AuditAction> failureActions = List.of(
                AuditAction.LOGIN_FAILED,
                AuditAction.UNAUTHORIZED_ACCESS
        );

        return auditLogRepository.findSuspiciousActivity(failureActions, since, threshold);
    }

    /**
     * Limpia logs antiguos (ejecutado semanalmente)
     */
    @Scheduled(cron = "${auth.audit-cleanup-cron:0 0 3 * * SUN}")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        log.info("Ejecutando limpieza de logs de auditoría anteriores a: {}", cutoffDate);
        auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
    }
}