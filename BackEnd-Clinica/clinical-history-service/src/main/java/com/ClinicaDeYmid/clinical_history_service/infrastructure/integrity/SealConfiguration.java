package com.ClinicaDeYmid.clinical_history_service.infrastructure.integrity;

import com.ClinicaDeYmid.clinical_history_service.domain.copy.RecordCopy;
import com.ClinicaDeYmid.commons.documents.DocumentSealer;
import com.ClinicaDeYmid.commons.documents.SealKeyRing;
import com.ClinicaDeYmid.commons.documents.TransitDocumentSealer;
import com.ClinicaDeYmid.commons.documents.TransitSealSigner;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class SealConfiguration {

    @Bean
    SealKeyRing sealKeyRing(SealProperties properties, TransitClient transit, TransitProperties transitProperties, Clock clock) {
        TransitKeys keys = new TransitKeys(transit, properties.transitKey(), transitProperties.keyRefreshInterval(), clock);
        return SealKeyRing.of(new TransitSealSigner(keys), properties.retiredPublicKeys());
    }

    @Bean
    EcdsaClinicalSignature clinicalSignature(SealKeyRing keys) {
        return new EcdsaClinicalSignature(keys);
    }

    @Bean
    DocumentSealer recordCopySealer(SealKeyRing keys) {
        return new TransitDocumentSealer(keys, RecordCopy.PURPOSE);
    }
}
