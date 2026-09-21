package com.ClinicaDeYmid.admissions_service.infrastructure.receipt;

import com.ClinicaDeYmid.admissions_service.domain.receipt.EpisodeReceipt;
import com.ClinicaDeYmid.commons.documents.DocumentIssuer;
import com.ClinicaDeYmid.commons.documents.DocumentSealer;
import com.ClinicaDeYmid.commons.documents.TransitDocumentSealer;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class ReceiptSealConfiguration {

    @Bean
    DocumentSealer receiptSealer(SealProperties properties, TransitClient transit, TransitProperties transitProperties,
                                 Clock clock) {
        return TransitDocumentSealer.with(transit, properties.transitKey(), transitProperties.keyRefreshInterval(),
                clock, properties.retiredPublicKeys(), EpisodeReceipt.PURPOSE);
    }

    @Bean
    DocumentIssuer receiptIssuer(DocumentSealer receiptSealer, Clock clock) {
        return new DocumentIssuer(receiptSealer, EpisodeReceipt.PURPOSE, clock);
    }
}
