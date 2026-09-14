package com.ClinicaDeYmid.clinical_history_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class ClinicalTestProperties {

    private ClinicalTestProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", TestJwt::publicKeyBase64);
        registry.add("clinica.clinical.seal.keys-location", TestSealKeys::directory);
        registry.add("clinica.clinical.seal.active-key-id", () -> TestSealKeys.ACTIVE_KEY_ID);
        encryption(registry);
        registry.add("eureka.client.enabled", () -> false);
    }

    public static void encryption(DynamicPropertyRegistry registry) {
        registry.add("clinica.clinical.encryption.keys-location", TestEncryptionKeys::directory);
        registry.add("clinica.clinical.encryption.active-key-id", () -> TestEncryptionKeys.ACTIVE_KEY_ID);
    }
}
