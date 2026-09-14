package com.ClinicaDeYmid.clinical_history_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@TestConfiguration(proxyBeanMethods = false)
public class OpenBaoTestContainer {

    public static final String ENCRYPTION_KEY = "clinical-kek";
    public static final String SEAL_KEY = "clinical-seal";
    public static final String ENCRYPTION_KEY_ID = ENCRYPTION_KEY + "-v1";
    public static final String SEAL_KEY_ID = SEAL_KEY + "-v1";

    private static final String TOKEN = "root";
    private static final GenericContainer<?> OPENBAO = start();

    @Bean
    VaultTemplate vaultTemplate() {
        return template();
    }

    public static VaultTemplate template() {
        return new VaultTemplate(VaultEndpoint.from(URI.create(uri())), new TokenAuthentication(TOKEN));
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.vault.enabled", () -> true);
        registry.add("spring.cloud.vault.uri", OpenBaoTestContainer::uri);
        registry.add("spring.cloud.vault.authentication", () -> "TOKEN");
        registry.add("spring.cloud.vault.token", () -> TOKEN);
    }

    public static String createKey(String prefix, String type) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        bao("write", "-f", "transit/keys/" + name, "type=" + type);
        return name;
    }

    public static void rotate(String key) {
        bao("write", "-f", "transit/keys/" + key + "/rotate");
    }

    private static String uri() {
        return "http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(8200);
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
        exec(container, "secrets", "enable", "transit");
        exec(container, "write", "-f", "transit/keys/" + ENCRYPTION_KEY, "type=aes256-gcm96");
        exec(container, "write", "-f", "transit/keys/" + SEAL_KEY, "type=ecdsa-p256");
        return container;
    }

    private static void bao(String... arguments) {
        exec(OPENBAO, arguments);
    }

    private static void exec(GenericContainer<?> container, String... arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = "bao";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            Container.ExecResult result = container.execInContainer(command);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("bao " + String.join(" ", arguments) + " failed: " + result.getStderr());
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
