package com.ClinicaDeYmid.auth_service.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.util.Locale;
import java.util.Map;

public final class SecretFiles {

    private static final String DIRECTORY = "/run/secrets/test/";

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
}
