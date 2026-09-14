package com.ClinicaDeYmid.patient_service.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.util.Locale;
import java.util.Map;

public final class SecretFiles {

    private static final String DIRECTORY = "/run/secrets/test/";
    private static final String KAFKA_CONNECT_DIRECTORY = "/run/secrets/kafka-connect/";

    private SecretFiles() {
    }

    public static <C extends GenericContainer<?>> C withSecretFiles(C container, Map<String, String> secrets) {
        secrets.forEach((variable, value) -> {
            String path = DIRECTORY + variable.toLowerCase(Locale.ROOT);
            container.withEnv(variable + "_FILE", path);
            container.withCopyToContainer(Transferable.of(value), path);
        });
        return container;
    }

    public static <C extends GenericContainer<?>> C withKafkaConnectSecrets(C container, Map<String, String> secrets) {
        container.withEnv("CONNECT_CONFIG_PROVIDERS", "env,dir");
        container.withEnv("CONNECT_CONFIG_PROVIDERS_ENV_CLASS", "org.apache.kafka.common.config.provider.EnvVarConfigProvider");
        container.withEnv("CONNECT_CONFIG_PROVIDERS_DIR_CLASS", "org.apache.kafka.common.config.provider.DirectoryConfigProvider");
        secrets.forEach((name, value) -> container.withCopyToContainer(Transferable.of(value), KAFKA_CONNECT_DIRECTORY + name));
        return container;
    }
}
