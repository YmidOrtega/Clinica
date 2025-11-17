// src/main/java/com/ClinicaDeYmid/patient_service/infra/security/UserContextHolder.java

package com.ClinicaDeYmid.patient_service.infra.security;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class UserContextHolder {

    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();

    /**
     * Establece el contexto del usuario para el thread actual
     */
    public static void setContext(UserContext context) {
        if (context == null) {
            log.warn("Intentando establecer UserContext null");
        }
        contextHolder.set(context);
    }

    /**
     * Obtiene el contexto del usuario del thread actual
     */
    public static UserContext getContext() {
        UserContext context = contextHolder.get();
        if (context == null) {
            log.debug("No hay UserContext en el thread actual");
        }
        return context;
    }

    /**
     * Limpia el contexto del usuario del thread actual
     * IMPORTANTE: Llamar esto al finalizar cada petición para evitar memory leaks
     */
    public static void clear() {
        contextHolder.remove();
    }

    /**
     * Obtiene el userId del contexto actual
     * @return userId o null si no hay contexto
     */
    public static Long getCurrentUserId() {
        UserContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * Obtiene el username del contexto actual
     * @return username o null si no hay contexto
     */
    public static String getCurrentUsername() {
        UserContext context = getContext();
        return context != null ? context.getUsername() : null;
    }

    /**
     * Verifica si hay un usuario autenticado en el contexto
     */
    public static boolean isAuthenticated() {
        return getContext() != null && getContext().getUserId() != null;
    }
}