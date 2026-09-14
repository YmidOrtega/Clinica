package com.ClinicaDeYmid.clinical_history_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class ClinicalTestProperties {

    private ClinicalTestProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", TestJwt::publicKeyBase64);
        OpenBaoTestContainer.register(registry);
        registry.add("eureka.client.enabled", () -> false);
    }
}
