package com.ClinicaDeYmid.api_gateway.infrastructure.oauth;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRedirectsTest {

    private static final URI HOME = URI.create("http://localhost:4321/");

    private final LoginRedirects redirects = new LoginRedirects(new GatewayProperties(
            new GatewayProperties.Frontend(List.of("http://localhost:4321", "https://app.clinica.co"), HOME), null, null,
            new GatewayProperties.RateLimit(1000, 300, Duration.ofMinutes(1)), null));

    @Test
    void returnsOnlyToTheFrontendOrigins() {
        assertThat(redirects.accepted("http://localhost:4321/pacientes?id=3")).hasToString("http://localhost:4321/pacientes?id=3");
        assertThat(redirects.accepted("https://app.clinica.co/historia")).hasToString("https://app.clinica.co/historia");
    }

    @Test
    void anythingElseFallsBackToTheHomePage() {
        assertThat(redirects.accepted("https://evil.test/")).isEqualTo(HOME);
        assertThat(redirects.accepted("http://localhost:4321@evil.test/")).isEqualTo(HOME);
        assertThat(redirects.accepted("https://user@app.clinica.co/")).isEqualTo(HOME);
        assertThat(redirects.accepted("//evil.test/")).isEqualTo(HOME);
        assertThat(redirects.accepted("/relative")).isEqualTo(HOME);
        assertThat(redirects.accepted("http://localhost:4321:bad")).isEqualTo(HOME);
        assertThat(redirects.accepted(null)).isEqualTo(HOME);
    }
}
