package com.ClinicaDeYmid.clinical_history_service.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class ClinicalTestProperties {

    private ClinicalTestProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", TestJwt::publicKeyBase64);
        OpenBaoTestContainer.register(registry);
        registry.add("clinica.clinical.encryption.transit-key", () -> ClinicalTransitKeys.ENCRYPTION_KEY);
        registry.add("clinica.clinical.seal.transit-key", () -> ClinicalTransitKeys.SEAL_KEY);
        registry.add("eureka.client.enabled", () -> false);
    }
}
