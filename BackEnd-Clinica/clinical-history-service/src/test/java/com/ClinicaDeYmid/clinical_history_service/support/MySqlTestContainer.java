package com.ClinicaDeYmid.clinical_history_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainer {

    public static final DockerImageName IMAGE = DockerImageName
            .parse(new ImageFromDockerfile("clinica/clinical-mysql-test", false)
                    .withFileFromPath(".", Path.of("docker/mysql"))
                    .get())
            .asCompatibleSubstituteFor("mysql");

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
