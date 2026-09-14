package com.ClinicaDeYmid.auth_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainer {

    public static final String IMAGE = "mysql:8.0";

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(IMAGE).withUsername("root");
    }

    @Bean
    public DynamicPropertyRegistrar flywayCredentials(MySQLContainer<?> mysql) {
        return registry -> {
            registry.add("spring.flyway.user", mysql::getUsername);
            registry.add("spring.flyway.password", mysql::getPassword);
        };
    }
}
