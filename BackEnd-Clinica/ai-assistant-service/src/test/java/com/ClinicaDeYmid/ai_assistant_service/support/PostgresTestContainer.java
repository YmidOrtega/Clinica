package com.ClinicaDeYmid.ai_assistant_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.util.Locale;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

    public static final String IMAGE = "postgres:16";
    public static final String MIGRATOR = "ai_assistant_migrator";
    public static final String MIGRATOR_PASSWORD = "migrator-test-secret";
    public static final String APP = "ai_assistant_app";
    public static final String APP_PASSWORD = "app-test-secret";

    private static final String SECRETS = "/run/secrets/test/";

    public static PostgreSQLContainer<?> withTheServiceUsers() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("ai_assistant_db")
                .withCopyFileToContainer(MountableFile.forHostPath("docker/postgres-init/01-create-users.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-create-users.sh");
        Map.of("AI_ASSISTANT_DB_MIGRATOR_USER", MIGRATOR,
                "AI_ASSISTANT_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD,
                "AI_ASSISTANT_DB_APP_USER", APP,
                "AI_ASSISTANT_DB_APP_PASSWORD", APP_PASSWORD).forEach((variable, value) -> {
            String path = SECRETS + variable.toLowerCase(Locale.ROOT);
            postgres.withEnv(variable + "_FILE", path);
            postgres.withCopyToContainer(Transferable.of(value), path);
        });
        return postgres;
    }

    public static String urlOf(PostgreSQLContainer<?> postgres) {
        return postgres.getJdbcUrl() + "&currentSchema=assistant";
    }

    @Bean
    public PostgreSQLContainer<?> postgresContainer() {
        return withTheServiceUsers();
    }

    @Bean
    public DynamicPropertyRegistrar serviceUsers(PostgreSQLContainer<?> postgres) {
        return registry -> {
            registry.add("spring.datasource.url", () -> urlOf(postgres));
            registry.add("spring.datasource.username", () -> APP);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
            registry.add("spring.flyway.url", () -> urlOf(postgres));
            registry.add("spring.flyway.user", () -> MIGRATOR);
            registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
        };
    }
}
