package com.ClinicaDeYmid.clinical_history_service.support;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;

import java.time.Instant;
import java.util.UUID;

public final class TestJwt {

    private TestJwt() {
    }

    public static String bearer(String role, UUID user) {
        return bearer(role, user, Instant.now());
    }

    public static String bearer(String role, UUID user, Instant authenticatedAt) {
        return SecurityTestTokens.staff(role, user).authenticatedAt(authenticatedAt).bearer();
    }
}
