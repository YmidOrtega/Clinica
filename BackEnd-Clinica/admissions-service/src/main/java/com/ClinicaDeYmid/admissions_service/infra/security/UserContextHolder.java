package com.ClinicaDeYmid.admissions_service.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Slf4j
public class UserContextHolder {

    private static final ThreadLocal<Long> userIdContext = new ThreadLocal<>();
    private static final ThreadLocal<String> usernameContext = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> rolesContext = new ThreadLocal<>();

    private UserContextHolder() {
        // Private constructor para prevenir instanciación
    }

    /**
     * Establece el userId en el contexto actual
     */
    public static void setUserId(Long userId) {
        userIdContext.set(userId);
        log.debug("UserContext: Set userId = {}", userId);
    }

    /**
     * Establece el username en el contexto actual
     */
    public static void setUsername(String username) {
        usernameContext.set(username);
        log.debug("UserContext: Set username = {}", username);
    }

    /**
     * Establece los roles en el contexto actual
     */
    public static void setRoles(List<String> roles) {
        rolesContext.set(roles);
        log.debug("UserContext: Set roles = {}", roles);
    }

    /**
     * Obtiene el userId del contexto actual
     * Primero intenta desde ThreadLocal, luego desde SecurityContext
     */
    public static Long getCurrentUserId() {
        // Intentar desde ThreadLocal primero
        Long userId = userIdContext.get();
        if (userId != null) {
            return userId;
        }

        // Fallback: intentar desde SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return userDetails.getUserId();
        }

        log.warn("No userId found in UserContext or SecurityContext");
        return null;
    }

    /**
     * Obtiene el username del contexto actual
     */
    public static String getCurrentUsername() {
        String username = usernameContext.get();
        if (username != null) {
            return username;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }

        return null;
    }

    /**
     * Obtiene los roles del contexto actual
     */
    public static List<String> getCurrentRoles() {
        List<String> roles = rolesContext.get();
        if (roles != null) {
            return roles;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String role = userDetails.getRole();
            return role != null ? List.of(role) : List.of();
        }

        return List.of();
    }

    /**
     * Limpia el contexto actual (importante para evitar memory leaks)
     */
    public static void clear() {
        userIdContext.remove();
        usernameContext.remove();
        rolesContext.remove();
        log.debug("UserContext cleared");
    }
}