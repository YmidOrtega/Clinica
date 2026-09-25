package com.ClinicaDeYmid.billing_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

public final class SharedMySql {

    public static final String IMAGE = "mysql:8.0";

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>(IMAGE).withUsername("root");

    static {
        CONTAINER.start();
    }

    private SharedMySql() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
        registry.add("spring.flyway.user", CONTAINER::getUsername);
        registry.add("spring.flyway.password", CONTAINER::getPassword);
    }
}
