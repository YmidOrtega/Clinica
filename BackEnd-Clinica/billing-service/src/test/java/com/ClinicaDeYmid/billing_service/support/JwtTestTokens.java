package com.ClinicaDeYmid.billing_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class JwtTestTokens {

    public static final Map<String, String> USERS = Map.of(
            "SUPER_ADMIN", "00000000-0000-4000-8000-000000000001",
            "ADMIN", "00000000-0000-4000-8000-000000000002",
            "DOCTOR", "00000000-0000-4000-8000-000000000003",
            "NURSE", "00000000-0000-4000-8000-000000000004",
            "RECEPTIONIST", "00000000-0000-4000-8000-000000000005",
            "BILLING", "00000000-0000-4000-8000-000000000008");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "billing-service");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.security.client.id", () -> "");
        registry.add("clinica.billing.admission-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        registry.add("clinica.billing.dian.software-id", () -> "56f2ae4e-9812-4fad-9255-643406bbb1a1");
        registry.add("clinica.billing.dian.software-pin", () -> "12345");
    }

    public static String bearer(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).bearer();
    }

    public static String bearerWithoutSecondFactor(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role))).withoutSecondFactor().bearer();
    }

    public static String bearerAuthenticatedLongAgo(String role) {
        return SecurityTestTokens.staff(role, UUID.fromString(USERS.get(role)))
                .authenticatedAt(Instant.now().minus(Duration.ofHours(12)))
                .bearer();
    }
}
