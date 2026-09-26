package com.ClinicaDeYmid.billing_service.infrastructure.config;

import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.application.dian.InvoiceSigner;
import com.ClinicaDeYmid.billing_service.infrastructure.dian.DianSigningKey;
import com.ClinicaDeYmid.billing_service.infrastructure.dian.TransitDianSigningKey;
import com.ClinicaDeYmid.billing_service.infrastructure.dian.UnavailableDianSigningKey;
import com.ClinicaDeYmid.billing_service.infrastructure.dian.XadesInvoiceSigner;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.vault.core.VaultOperations;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class DianConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DianConfiguration.class);

    @Bean
    DianSoftware dianSoftware(@Value("${clinica.billing.dian.software-id}") String softwareId,
                              @Value("${clinica.billing.dian.software-pin}") String pin) {
        return new DianSoftware(softwareId, pin);
    }

    @Bean
    @ConditionalOnMissingBean
    DianSigningKey dianSigningKey(ObjectProvider<TransitClient> transit, ObjectProvider<VaultOperations> vault,
                                  ObjectProvider<TransitProperties> transitProperties,
                                  @Value("${clinica.billing.dian.signing-key}") String keyName,
                                  @Value("${clinica.billing.dian.certificate-mount}") String mount,
                                  @Value("${clinica.billing.dian.certificate-path}") String certificatePath,
                                  Clock clock) {
        TransitClient client = transit.getIfAvailable();
        VaultOperations operations = vault.getIfAvailable();
        TransitProperties properties = transitProperties.getIfAvailable();
        if (client == null || operations == null || properties == null) {
            log.warn("OpenBao is disabled: issued invoices stay unsigned");
            return new UnavailableDianSigningKey();
        }
        return new TransitDianSigningKey(client, operations, keyName, mount, certificatePath,
                properties.keyRefreshInterval(), clock);
    }

    @Bean
    InvoiceSigner invoiceSigner(DianSigningKey key, @Value("${clinica.billing.time-zone}") ZoneId zone) {
        return new XadesInvoiceSigner(key, zone);
    }
}
