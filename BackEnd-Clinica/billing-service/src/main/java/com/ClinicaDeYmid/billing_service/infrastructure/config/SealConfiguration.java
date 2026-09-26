package com.ClinicaDeYmid.billing_service.infrastructure.config;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentation;
import com.ClinicaDeYmid.commons.documents.DocumentIssuer;
import com.ClinicaDeYmid.commons.documents.DocumentSeal;
import com.ClinicaDeYmid.commons.documents.DocumentSealer;
import com.ClinicaDeYmid.commons.documents.TransitDocumentSealer;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SealProperties.class)
public class SealConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SealConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    DocumentSealer representationSealer(SealProperties properties, ObjectProvider<TransitClient> transit,
                                        ObjectProvider<TransitProperties> transitProperties, Clock clock) {
        TransitClient client = transit.getIfAvailable();
        TransitProperties refresh = transitProperties.getIfAvailable();
        if (client == null || refresh == null) {
            log.warn("OpenBao is disabled: graphic representations cannot be sealed");
            return new Unavailable();
        }
        return TransitDocumentSealer.with(client, properties.transitKey(), refresh.keyRefreshInterval(), clock,
                properties.retiredPublicKeys() == null ? Map.of() : properties.retiredPublicKeys(),
                GraphicRepresentation.PURPOSE);
    }

    @Bean
    DocumentIssuer representationIssuer(DocumentSealer representationSealer, Clock clock) {
        return new DocumentIssuer(representationSealer, GraphicRepresentation.PURPOSE, clock);
    }

    static final class Unavailable implements DocumentSealer {

        @Override
        public DocumentSeal seal(String sha256) {
            throw new BillingException.SealUnavailable();
        }

        @Override
        public boolean verify(String sha256, DocumentSeal seal) {
            throw new BillingException.SealUnavailable();
        }

        @Override
        public String activeKeyId() {
            throw new BillingException.SealUnavailable();
        }

        @Override
        public Map<String, String> publicKeysPem() {
            return Map.of();
        }
    }
}
