package com.ClinicaDeYmid.commons.openbao.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OpenBaoTestContainer {

    public static final String TOKEN = "root";

    private static final GenericContainer<?> OPENBAO = start();

    private OpenBaoTestContainer() {
    }

    public static VaultTemplate template() {
        return new VaultTemplate(VaultEndpoint.from(URI.create(uri())), new TokenAuthentication(TOKEN));
    }

    public static String uri() {
        return "http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(8200);
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.vault.enabled", () -> true);
        registry.add("spring.cloud.vault.uri", OpenBaoTestContainer::uri);
        registry.add("spring.cloud.vault.authentication", () -> "TOKEN");
        registry.add("spring.cloud.vault.token", () -> TOKEN);
    }

    public static synchronized String ensureKey(String name, String type) {
        if (exec("read", "transit/keys/" + name).getExitCode() != 0) {
            bao("write", "-f", "transit/keys/" + name, "type=" + type);
        }
        return name;
    }

    public static String createKey(String prefix, String type) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        bao("write", "-f", "transit/keys/" + name, "type=" + type);
        return name;
    }

    public static String currentTotpCode(String otpauthUrl) {
        return TotpCodes.at(otpauthUrl, Instant.now());
    }

    public static void rotate(String key) {
        bao("write", "-f", "transit/keys/" + key + "/rotate");
    }

    public static void putSecret(String path, Map<String, String> values) {
        List<String> arguments = new ArrayList<>(List.of("kv", "put", "-mount=secret", path));
        values.forEach((key, value) -> arguments.add(key + "=" + value));
        bao(arguments.toArray(String[]::new));
    }

    private static GenericContainer<?> start() {
        GenericContainer<?> container = new GenericContainer<>("openbao/openbao:2.6.2")
                .withCommand("server", "-dev")
                .withEnv("BAO_DEV_ROOT_TOKEN_ID", TOKEN)
                .withEnv("BAO_ADDR", "http://127.0.0.1:8200")
                .withEnv("BAO_TOKEN", TOKEN)
                .withExposedPorts(8200)
                .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200));
        container.start();
        for (String engine : new String[]{"transit", "totp"}) {
            Container.ExecResult enabled = run(container, "secrets", "enable", engine);
            if (enabled.getExitCode() != 0) {
                throw new IllegalStateException("Could not enable " + engine + ": " + enabled.getStderr());
            }
        }
        return container;
    }

    private static void bao(String... arguments) {
        Container.ExecResult result = exec(arguments);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("bao " + String.join(" ", arguments) + " failed: " + result.getStderr());
        }
    }

    private static Container.ExecResult exec(String... arguments) {
        return run(OPENBAO, arguments);
    }

    private static Container.ExecResult run(GenericContainer<?> container, String... arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = "bao";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            return container.execInContainer(command);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
