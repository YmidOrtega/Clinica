package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.user.entity.PasswordHistory;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${auth.password.min-length:8}")
    private int minLength;

    @Value("${auth.password.require-uppercase:true}")
    private boolean requireUppercase;

    @Value("${auth.password.require-lowercase:true}")
    private boolean requireLowercase;

    @Value("${auth.password.require-digit:true}")
    private boolean requireDigit;

    @Value("${auth.password.require-special-char:true}")
    private boolean requireSpecialChar;

    @Value("${auth.password.history-count:5}")
    private int passwordHistoryCount;

    @Value("${auth.password.expiration-days:90}")
    private int passwordExpirationDays;

    // Patrones regex
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");

    /**
     * Válida que una contraseña cumpla con todas las políticas
     */
    public PasswordValidationResult validatePassword(String password) {
        log.debug("Validando contraseña contra políticas de seguridad");

        List<String> violations = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            violations.add("La contraseña no puede estar vacía");
            return new PasswordValidationResult(false, violations);
        }

        // Longitud mínima
        if (password.length() < minLength) {
            violations.add(String.format("La contraseña debe tener al menos %d caracteres", minLength));
        }

        // Mayúsculas
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            violations.add("La contraseña debe contener al menos una letra mayúscula");
        }

        // Minúsculas
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).find()) {
            violations.add("La contraseña debe contener al menos una letra minúscula");
        }

        // Dígitos
        if (requireDigit && !DIGIT_PATTERN.matcher(password).find()) {
            violations.add("La contraseña debe contener al menos un número");
        }

        // Caracteres especiales
        if (requireSpecialChar && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            violations.add("La contraseña debe contener al menos un carácter especial (!@#$%^&*(),.?\":{}|<>)");
        }

        boolean isValid = violations.isEmpty();

        if (isValid) {
            log.debug("Contraseña cumple con todas las políticas");
        } else {
            log.warn("Contraseña no cumple con {} políticas", violations.size());
        }

        return new PasswordValidationResult(isValid, violations);
    }

    /**
     * Verifica si una contraseña ya fue usada recientemente
     */
    public boolean isPasswordInHistory(User user, String rawPassword) {
        log.debug("Verificando si contraseña fue usada recientemente por usuario: {}", user.getEmail());

        List<PasswordHistory> recentPasswords = passwordHistoryRepository
                .findRecentPasswordsByUser(user, passwordHistoryCount);

        boolean isInHistory = recentPasswords.stream()
                .anyMatch(ph -> passwordEncoder.matches(rawPassword, ph.getPasswordHash()));

        if (isInHistory) {
            log.warn("Usuario {} intentó reutilizar una contraseña reciente", user.getEmail());
        }

        return isInHistory;
    }

    /**
     * Guarda la contraseña actual en el historial
     */
    @Transactional
    public void savePasswordToHistory(User user) {
        log.debug("Guardando contraseña en historial para usuario: {}", user.getEmail());

        PasswordHistory passwordHistory = PasswordHistory.builder()
                .user(user)
                .passwordHash(user.getPassword())
                .build();

        passwordHistoryRepository.save(passwordHistory);

        // Limitar el historial al número configurado
        cleanupOldPasswords(user);
    }

    /**
     * Limpia contraseña antiguas del historial
     */
    @Transactional
    public void cleanupOldPasswords(User user) {
        long count = passwordHistoryRepository.countByUser(user);

        if (count > passwordHistoryCount) {
            log.debug("Limpiando contraseñas antiguas para usuario: {}", user.getEmail());
            passwordHistoryRepository.deleteOldPasswordsExceptRecent(user, passwordHistoryCount);
        }
    }

    /**
     * Verifica si la contraseña del usuario ha expirado
     */
    public boolean isPasswordExpired(User user) {
        if (user.isPasswordNeverExpires()) {
            return false;
        }

        if (user.getLastPasswordChange() == null) {
            log.warn("Usuario {} no tiene fecha de último cambio de contraseña", user.getEmail());
            return true;
        }

        LocalDateTime expirationDate = user.getLastPasswordChange().plusDays(passwordExpirationDays);
        boolean isExpired = LocalDateTime.now().isAfter(expirationDate);

        if (isExpired) {
            log.info("Contraseña expirada para usuario: {}", user.getEmail());
        }

        return isExpired;
    }

    /**
     * Obtiene información sobre la política de contraseñas
     */
    public PasswordPolicyInfo getPasswordPolicyInfo() {
        return new PasswordPolicyInfo(
                minLength,
                requireUppercase,
                requireLowercase,
                requireDigit,
                requireSpecialChar,
                passwordHistoryCount,
                passwordExpirationDays
        );
    }

    public record PasswordValidationResult(
            boolean isValid,
            List<String> violations
    ) {
        public String getViolationsMessage() {
            return String.join("; ", violations);
        }
    }

    public record PasswordPolicyInfo(
            int minLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireDigit,
            boolean requireSpecialChar,
            int passwordHistoryCount,
            int passwordExpirationDays
    ) {}
}