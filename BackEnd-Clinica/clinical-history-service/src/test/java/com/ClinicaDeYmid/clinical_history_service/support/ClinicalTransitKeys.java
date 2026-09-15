package com.ClinicaDeYmid.clinical_history_service.support;

import com.ClinicaDeYmid.commons.openbao.testing.OpenBaoTestContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;

@TestConfiguration(proxyBeanMethods = false)
public class ClinicalTransitKeys {

    public static final String ENCRYPTION_KEY = OpenBaoTestContainer.ensureKey("clinical-kek", "aes256-gcm96");
    public static final String SEAL_KEY = OpenBaoTestContainer.ensureKey("clinical-seal", "ecdsa-p256");
    public static final String ENCRYPTION_KEY_ID = ENCRYPTION_KEY + "-v1";
    public static final String SEAL_KEY_ID = SEAL_KEY + "-v1";

    @Bean
    VaultTemplate vaultTemplate() {
        return OpenBaoTestContainer.template();
    }
}
