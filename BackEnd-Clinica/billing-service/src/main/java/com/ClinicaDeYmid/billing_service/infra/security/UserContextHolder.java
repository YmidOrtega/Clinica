package com.ClinicaDeYmid.billing_service.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class UserContextHolder {

    private static final String SYSTEM_USER = "SYSTEM";

    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void setCurrentUserId(String userId) {
        userIdHolder.set(userId);
    }

    /**
     * Identificador del usuario para las columnas de auditoría (created_by / updated_by).
     * <p>
     * Prioridad: ThreadLocal → email del token → uuid del token → "SYSTEM".
     * Se prefiere el email por legibilidad en la auditoría; el uuid queda como respaldo
     * cuando el token no trae el claim.
     */
    public static String getCurrentUserId() {
        String userId = userIdHolder.get();
        if (userId != null) {
            return userId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            if (userDetails.getEmail() != null) {
                return userDetails.getEmail();
            }
            if (userDetails.getUuid() != null) {
                return userDetails.getUuid();
            }
        }

        log.warn("No se encontró usuario en el contexto de seguridad; se audita como {}", SYSTEM_USER);
        return SYSTEM_USER;
    }

    public static void clear() {
        userIdHolder.remove();
    }
}
