package com.ClinicaDeYmid.ai_assistant_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "BILLING", "00000000-0000-4000-8000-000000000008",
            "ACCOUNTS_RECEIVABLE", "00000000-0000-4000-8000-000000000010",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "ai-assistant-service");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.security.client.id", () -> "");
        registry.add("clinica.assistant.invoice-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
    }

    public static String bearer(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).bearer();
    }

    public static String bearerWithoutSecondFactor(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).withoutSecondFactor().bearer();
    }

    public static String bearerAs(String role, UUID user) {
        return SecurityTestTokens.staff(role, user).bearer();
    }
}
