package com.ClinicaDeYmid.admissions_service.support;

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
            "MEDICAL_RECORDS", "00000000-0000-4000-8000-000000000006",
            "BILLING", "00000000-0000-4000-8000-000000000008");

    private JwtTestTokens() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "admissions-service");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("clinica.admissions.receipts.public-checks-per-window", () -> 20);
        registry.add("clinica.security.client.id", () -> "");
        registry.add("clinica.admissions.patient-events.enabled", () -> false);
        registry.add("clinica.admissions.practitioner-events.enabled", () -> false);
        registry.add("clinica.admissions.clinical-events.enabled", () -> false);
        registry.add("clinica.admissions.seal.transit-key", () -> TransitKeys.SEAL_KEY);
        registry.add("clinica.security.client.assertion-key", () -> TransitKeys.CLIENT_KEY);
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
