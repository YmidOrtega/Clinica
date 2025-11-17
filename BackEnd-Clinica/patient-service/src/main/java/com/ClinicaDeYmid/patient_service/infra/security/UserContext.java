package com.ClinicaDeYmid.patient_service.infra.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    private Long userId;
    private String username;
    private List<String> roles;
    private String ipAddress;
    private String userAgent;

    /**
     * Verifica si el usuario tiene un rol específico
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Verifica si el usuario tiene alguno de los roles especificados
     */
    public boolean hasAnyRole(String... roles) {
        if (this.roles == null) {
            return false;
        }
        for (String role : roles) {
            if (this.roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica si el usuario es administrador
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Verifica si el usuario es doctor
     */
    public boolean isDoctor() {
        return hasRole("DOCTOR");
    }

    /**
     * Verifica si el usuario es enfermera
     */
    public boolean isNurse() {
        return hasRole("NURSE");
    }
}