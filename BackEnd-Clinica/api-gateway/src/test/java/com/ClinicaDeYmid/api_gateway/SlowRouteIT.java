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

import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.AUTH;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.FRONTEND;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.SERVICES;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.publishSigningKey;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.tokenResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SlowRouteIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        GatewayTestSupport.register(registry);
        registry.add("spring.http.client.read-timeout", () -> "1s");
        registry.add("clinica.gateway.assistant.read-timeout", () -> "5s");
    }

    @BeforeEach
    void resetUpstreams() {
        AUTH.resetAll();
        SERVICES.resetAll();
        publishSigningKey();
    }

    @Test
    void theAssistantMayTakeLongerThanTheOtherServicesToAnswer() {
        Browser browser = new Browser(port);
        signIn(browser);
        SERVICES.stubFor(get("/api/v1/assistant/findings").willReturn(okJson("{\"content\": []}").withFixedDelay(2000)));
        SERVICES.stubFor(get("/api/v1/billing/invoices/123").willReturn(okJson("{}").withFixedDelay(2000)));

        assertThat(browser.get("/api/v1/assistant/findings", "Origin", FRONTEND).status()).isEqualTo(200);
        assertThat(browser.get("/api/v1/billing/invoices/123", "Origin", FRONTEND).status()).isGreaterThanOrEqualTo(500);
        SERVICES.verify(getRequestedFor(urlPathEqualTo("/api/v1/assistant/findings"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withoutHeader("Cookie"));
    }

    private void signIn(Browser browser) {
        String returnTo = FRONTEND + "/facturacion";
        Browser.Response start = browser.get("/bff/login?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8));
        Browser.Response authorize = browser.get(start.location());
        Map<String, String> query = Browser.query(authorize.location());
        AUTH.stubFor(post("/oauth2/token").withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(okJson(tokenResponse(query.get("nonce"), "access-1", "refresh-1", 300))));
        Browser.Response callback = browser.get("/login/oauth2/code/clinica?code=code-1&state="
                + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8));
        assertThat(callback.location()).isEqualTo(returnTo);
    }
}
