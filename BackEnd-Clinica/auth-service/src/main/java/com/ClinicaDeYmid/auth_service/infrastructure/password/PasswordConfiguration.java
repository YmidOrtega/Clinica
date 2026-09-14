package com.ClinicaDeYmid.auth_service.infrastructure.password;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordDenyList;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Argon2Properties.class)
public class PasswordConfiguration {

    @Bean
    PasswordHasher passwordHasher(Argon2Properties properties) {
        return new Argon2PasswordHasher(properties);
    }

    @Bean
    PasswordDenyList passwordDenyList(@Value("${clinica.auth.password-deny-lists}") List<Resource> resources) {
        return new ClasspathPasswordDenyList(resources);
    }

    @Bean
    PasswordPolicy passwordPolicy(PasswordDenyList denyList, @Value("${clinica.auth.password-service-words}") List<String> serviceWords) {
        return new PasswordPolicy(denyList, serviceWords);
    }
}
