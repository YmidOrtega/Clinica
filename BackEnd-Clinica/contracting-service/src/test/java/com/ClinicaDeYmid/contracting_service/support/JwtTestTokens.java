package com.ClinicaDeYmid.contracting_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "ADMIN", "00000000-0000-4000-8000-000000000002",
            "CONTRACTING", "00000000-0000-4000-8000-000000000007",
            "BILLING", "00000000-0000-4000-8000-000000000008",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005",
            "DOCTOR", "00000000-0000-4000-8000-000000000003");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "contracting-service");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.security.client.id", () -> "");
    }

    public static String bearer(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).bearer();
    }
}
