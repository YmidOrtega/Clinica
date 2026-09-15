package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EncryptionProperties.class)
public class EncryptionConfiguration {

    @Bean
    ContentEncryption contentEncryption(EncryptionProperties properties, TransitClient transit, TransitProperties transitProperties,
                                        NamedParameterJdbcTemplate jdbc, TransactionOperations transactions, Clock clock) {
        MasterKeys masterKeys = MasterKeys.of(
                new TransitKeyEncryptionKeys(new TransitKeys(transit, properties.transitKey(), transitProperties.keyRefreshInterval(), clock)),
                LocalKeyEncryptionKeys.retired(properties.retiredMasterKeys()));
        return new ContentEncryption(masterKeys, new DataKeyStore(jdbc), transactions, clock);
    }
}
