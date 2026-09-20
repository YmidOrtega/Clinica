package com.ClinicaDeYmid.admissions_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public final class SharedPostgres {

    public static final String IMAGE = "postgres:17-alpine";

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(IMAGE);

    static {
        CONTAINER.start();
    }

    private SharedPostgres() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
        registry.add("spring.flyway.user", CONTAINER::getUsername);
        registry.add("spring.flyway.password", CONTAINER::getPassword);
    }
}
