package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitClient;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitKeys;
import com.ClinicaDeYmid.clinical_history_service.infrastructure.transit.TransitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class SealConfiguration {

    @Bean
    EcdsaClinicalSignature clinicalSignature(SealProperties properties, TransitClient transit, TransitProperties transitProperties, Clock clock) {
        TransitKeys keys = new TransitKeys(transit, properties.transitKey(), transitProperties.keyRefreshInterval(), clock);
        return new EcdsaClinicalSignature(SealKeyRing.of(new TransitSealSigner(keys), properties.retiredPublicKeys()));
    }
}
