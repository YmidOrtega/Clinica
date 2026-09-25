package com.ClinicaDeYmid.practitioners_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "ADMIN", "00000000-0000-4000-8000-000000000002",
            "HUMAN_RESOURCES", "00000000-0000-4000-8000-000000000009",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005",
            "DOCTOR", "00000000-0000-4000-8000-000000000003",
            "BILLING", "00000000-0000-4000-8000-000000000008");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "practitioners-service");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.security.client.id", () -> "");
    }

    public static String bearer(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).bearer();
    }
}
