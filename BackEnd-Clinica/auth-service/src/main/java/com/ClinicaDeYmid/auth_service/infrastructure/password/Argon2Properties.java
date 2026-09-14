package com.ClinicaDeYmid.auth_service.infrastructure.password;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("clinica.auth.argon2")
record Argon2Properties(@DefaultValue("16") int saltLength, @DefaultValue("32") int hashLength, @DefaultValue("1") int parallelism,
                        @DefaultValue("19456") int memoryKib, @DefaultValue("2") int iterations) {
}
