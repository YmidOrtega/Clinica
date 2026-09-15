package com.ClinicaDeYmid.api_gateway;

import com.ClinicaDeYmid.api_gateway.support.Browser;
import com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        GatewayTestSupport.register(registry);
        registry.add("clinica.gateway.rate-limit.per-address", () -> 5);
        registry.add("clinica.gateway.rate-limit.window", () -> "1h");
    }

    @BeforeEach
    void forgetEarlierRequests() throws Exception {
        GatewayTestSupport.REDIS.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    void anAddressThatExceedsItsLimitIsDelayedUntilTheWindowEnds() {
        Browser browser = new Browser(port);

        IntStream.range(0, 5).forEach(request -> assertThat(browser.get("/bff/session").status()).isEqualTo(200));
        Browser.Response limited = browser.get("/bff/session");

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.json().get("code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(3500L, 3600L);
        assertThat(browser.get("/actuator/health").status()).isEqualTo(200);
    }
}
