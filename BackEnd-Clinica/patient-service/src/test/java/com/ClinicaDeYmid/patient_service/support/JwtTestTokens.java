package com.ClinicaDeYmid.patient_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "ADMIN", "00000000-0000-4000-8000-000000000002",
            "DOCTOR", "00000000-0000-4000-8000-000000000003",
            "NURSE", "00000000-0000-4000-8000-000000000004",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005",
            "MEDICAL_RECORDS", "00000000-0000-4000-8000-000000000006");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "patient-service");
    }

    public static String bearer(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).bearer();
    }
}
