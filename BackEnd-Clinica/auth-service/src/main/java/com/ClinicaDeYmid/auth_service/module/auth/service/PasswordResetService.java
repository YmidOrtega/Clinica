package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.auth.entity.PasswordResetToken;
import com.ClinicaDeYmid.auth_service.module.auth.repository.PasswordResetTokenRepository;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditLogService auditLogService;
    // TODO: Aquí inyectarías tu EmailService cuando lo implementes
    // private final EmailService emailService;

    /**
     * Inicia el proceso de reseteo de contraseña
     */
    @Transactional
    public void initiatePasswordReset(String email) {
        log.info("Iniciando proceso de reseteo de contraseña para: {}", email);

        var userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            log.warn("Solicitud de reseteo para email no registrado: {}", email);
            // Por seguridad, retornamos silenciosamente para evitar enumeración de usuarios
            return;
        }

        User user = userOptional.get();

        // Invalidar tokens previos
        tokenRepository.invalidateAllUserTokens(user, LocalDateTime.now());

        // Crear nuevo token
        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenValue)
                .user(user)
                .build();

        tokenRepository.save(token);

        // Auditar la acción
        auditLogService.logPasswordResetRequested(user);

        // TODO: Enviar email con el token
        // emailService.sendPasswordResetEmail(user.getEmail(), tokenValue);

        log.info("Proceso de reseteo iniciado correctamente para usuario: {}", email);
    }

    /**
     * Valida un token de reseteo de contraseña
     */
    public boolean validateResetToken(String token) {
        return tokenRepository.findValidToken(token, LocalDateTime.now()).isPresent();
    }

    /**
     * Resetea la contraseña usando un token válido
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("Intentando resetear contraseña con token");

        // Validar el token
        PasswordResetToken resetToken = tokenRepository.findValidToken(token, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o expirado"));

        User user = resetToken.getUser();

        // Validar la nueva contraseña contra políticas
        var validationResult = passwordPolicyService.validatePassword(newPassword);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getViolationsMessage());
        }

        // Verificar que no esté en el historial
        if (passwordPolicyService.isPasswordInHistory(user, newPassword)) {
            throw new IllegalArgumentException(
                    "No puedes usar una de tus últimas " +
                            passwordPolicyService.getPasswordPolicyInfo().passwordHistoryCount() +
                            " contraseñas"
            );
        }

        // Guardar contraseña actual en historial antes de cambiarla
        if (user.getPassword() != null) {
            passwordPolicyService.savePasswordToHistory(user);
        }

        // Actualizar contraseña
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.updateLastPasswordChange(user.getId(), LocalDateTime.now());
        userRepository.save(user);

        // Marcar token como usado
        resetToken.markAsUsed();
        tokenRepository.save(resetToken);

        // Auditar la acción
        auditLogService.logPasswordResetCompleted(user);

        log.info("Contraseña reseteada exitosamente para usuario: {}", user.getEmail());
    }

    /**
     * Verifica si un usuario tiene un token válido pendiente
     */
    public boolean hasValidToken(User user) {
        return tokenRepository.hasValidToken(user, LocalDateTime.now());
    }

    /**
     * Limpia tokens expirados (ejecutado diariamente)
     */
    @Scheduled(cron = "${auth.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Ejecutando limpieza de tokens de reseteo expirados");

        int deletedCount = tokenRepository.deleteExpiredTokens(LocalDateTime.now());

        if (deletedCount > 0) {
            log.info("Se eliminaron {} tokens de reseteo expirados", deletedCount);
        }
    }
}