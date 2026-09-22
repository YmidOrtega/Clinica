package com.ClinicaDeYmid.api_gateway;

import com.ClinicaDeYmid.api_gateway.support.Browser;
import com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        GatewayTestSupport.register(registry);
        registry.add("clinica.gateway.rate-limit.per-address", () -> 50);
        registry.add("clinica.gateway.rate-limit.per-public-address", () -> 2);
        registry.add("clinica.gateway.rate-limit.per-user", () -> 3);
        registry.add("clinica.gateway.rate-limit.window", () -> "1h");
    }

    @BeforeEach
    void forgetEarlierRequests() throws Exception {
        GatewayTestSupport.REDIS.execInContainer("redis-cli", "FLUSHALL");
        GatewayTestSupport.AUTH.resetAll();
        GatewayTestSupport.SERVICES.resetAll();
        GatewayTestSupport.publishSigningKey();
    }

    private void signIn(Browser browser) {
        Browser.Response start = browser.get("/bff/login?returnTo="
                + URLEncoder.encode(GatewayTestSupport.FRONTEND + "/inicio", StandardCharsets.UTF_8));
        Browser.Response authorize = browser.get(start.location());
        Map<String, String> query = Browser.query(authorize.location());
        GatewayTestSupport.AUTH.stubFor(post("/oauth2/token").withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(okJson(GatewayTestSupport.tokenResponse(query.get("nonce"), "access-1", "refresh-1", 300))));
        browser.get("/login/oauth2/code/clinica?code=code-1&state="
                + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8));
    }

    @Test
    void anAddressThatExceedsItsLimitIsDelayedUntilTheWindowEnds() {
        Browser browser = new Browser(port);

        IntStream.range(0, 50).forEach(request -> assertThat(browser.get("/bff/session").status()).isEqualTo(200));
        Browser.Response limited = browser.get("/bff/session");

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.json().get("code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(3500L, 3600L);
        assertThat(browser.get("/actuator/health").status()).isEqualTo(200);
    }

    @Test
    void aSignedInUserHasTheirOwnLimitOnTopOfTheirAddress() {
        Browser browser = new Browser(port);
        signIn(browser);
        GatewayTestSupport.SERVICES.stubFor(get("/api/v1/patients/123").willReturn(okJson("{}")));

        int allowed = 0;
        Browser.Response last = null;
        for (int attempt = 0; attempt < 6 && (last == null || last.status() == 200); attempt++) {
            last = browser.get("/api/v1/patients/123");
            if (last.status() == 200) {
                allowed++;
            }
        }

        assertThat(allowed).as("the user spends their own quota").isBetween(1, 3);
        assertThat(last.status()).isEqualTo(429);
        assertThat(last.json().get("code").asText()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void theDoorWithoutASessionRunsOutSoonerThanTheRest() {
        Browser browser = new Browser(port);
        GatewayTestSupport.SERVICES.stubFor(get("/api/v1/admissions/seal-keys").willReturn(okJson("{}")));

        IntStream.range(0, 2).forEach(request ->
                assertThat(browser.get("/api/v1/admissions/seal-keys").status()).isEqualTo(200));
        Browser.Response limited = browser.get("/api/v1/admissions/seal-keys");

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.json().get("code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(browser.get("/bff/session").status()).isEqualTo(200);
    }
}
