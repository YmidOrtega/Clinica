package com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption;

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
    ContentEncryption contentEncryption(EncryptionProperties properties, NamedParameterJdbcTemplate jdbc, TransactionOperations transactions,
                                        Clock clock) {
        return new ContentEncryption(MasterKeys.load(properties), new DataKeyStore(jdbc), transactions, clock);
    }
}
