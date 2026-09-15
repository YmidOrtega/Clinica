package com.ClinicaDeYmid.clinical_history_service.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class ClinicalTestProperties {

    public static final String CLIENT_ASSERTION_KEY = OpenBaoTestContainer.ensureKey("clinical-history-service-client", "ecdsa-p256");

    private ClinicalTestProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, "clinical-history-service");
        registry.add("clinica.security.client.assertion-key", () -> CLIENT_ASSERTION_KEY);
        OpenBaoTestContainer.register(registry);
        registry.add("clinica.clinical.encryption.transit-key", () -> ClinicalTransitKeys.ENCRYPTION_KEY);
        registry.add("clinica.clinical.seal.transit-key", () -> ClinicalTransitKeys.SEAL_KEY);
        registry.add("eureka.client.enabled", () -> false);
    }
}
