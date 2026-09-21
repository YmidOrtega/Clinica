package com.ClinicaDeYmid.admissions_service.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@TestConfiguration(proxyBeanMethods = false)
public class TransitKeys {

    public static final String SEAL_KEY = OpenBaoTestContainer.ensureKey("admissions-seal", "ecdsa-p256");
    public static final String CLIENT_KEY = OpenBaoTestContainer.ensureKey("admissions-service-client", "ecdsa-p256");
    public static final String SEAL_KEY_ID = SEAL_KEY + "-v1";

    @Bean
    VaultTemplate vaultTemplate() {
        return OpenBaoTestContainer.template();
    }
}
