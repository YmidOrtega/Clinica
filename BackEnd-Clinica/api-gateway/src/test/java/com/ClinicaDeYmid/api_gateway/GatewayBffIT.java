package com.ClinicaDeYmid.api_gateway;

import com.ClinicaDeYmid.api_gateway.support.Browser;
import com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.AUTH;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.FRONTEND;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.ISSUER;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.SERVICES;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.publishSigningKey;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.refreshResponse;
import static com.ClinicaDeYmid.api_gateway.support.GatewayTestSupport.tokenResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayBffIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        GatewayTestSupport.register(registry);
    }

    @BeforeEach
    void resetUpstreams() {
        AUTH.resetAll();
        SERVICES.resetAll();
        publishSigningKey();
    }

    @Test
    void signsInThroughAuthServiceAndRelaysTheStaffTokenWithoutBrowserCredentials() {
        Browser browser = new Browser(port);
        JsonNode anonymous = browser.get("/bff/session").json();
        assertThat(anonymous.get("authenticated").asBoolean()).isFalse();
        assertThat(anonymous.get("csrf").get("token").asText()).isNotBlank();
        assertThat(browser.get("/api/v1/patients/123").status()).isEqualTo(401);

        signIn(browser, FRONTEND + "/pacientes", 300);

        AUTH.verify(postRequestedFor(urlPathEqualTo("/oauth2/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(matching("^(?!.*client_id=.*client_id=).*client_id=api-gateway.*$"))
                .withRequestBody(containing("client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"))
                .withRequestBody(containing("client_assertion=ey"))
                .withRequestBody(containing("code_verifier=")));
        JsonNode session = browser.get("/bff/session").json();
        assertThat(session.get("authenticated").asBoolean()).isTrue();
        assertThat(session.get("user").get("role").asText()).isEqualTo("DOCTOR");
        assertThat(session.get("user").get("methods")).extracting(JsonNode::asText).containsExactly("pwd", "otp", "mfa");

        SERVICES.stubFor(get("/api/v1/patients/123").willReturn(okJson("{\"uuid\": \"123\"}").withHeader("Access-Control-Allow-Origin", "*")
                .withHeader("ETag", "\"4\"")));
        Browser.Response patient = browser.get("/api/v1/patients/123", "Origin", FRONTEND, "Authorization", "Bearer forged");
        assertThat(patient.status()).isEqualTo(200);
        assertThat(patient.header("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
        assertThat(patient.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        SERVICES.verify(getRequestedFor(urlPathEqualTo("/api/v1/patients/123"))
                .withHeader("Authorization", equalTo("Bearer access-1"))
                .withHeader("Cookie", absent()));

        SERVICES.stubFor(post("/api/v1/patients").willReturn(aResponse().withStatus(201)));
        Browser.Response withoutCsrf = browser.send("POST", "/api/v1/patients", "{}", "Content-Type", "application/json");
        assertThat(withoutCsrf.status()).isEqualTo(403);
        assertThat(withoutCsrf.json().get("code").asText()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(browser.withCsrf("POST", "/api/v1/patients", "{}").status()).isEqualTo(201);
    }

    @Test
    void parallelRequestsWithAnExpiringTokenRefreshItOnlyOnce() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/", 5);
        AUTH.stubFor(post("/oauth2/token").withRequestBody(containing("grant_type=refresh_token"))
                .willReturn(okJson(refreshResponse("access-2", "refresh-2")).withFixedDelay(300)));
        SERVICES.stubFor(get("/api/v1/clinical/encounters").willReturn(okJson("[]")));

        List<Integer> statuses = IntStream.range(0, 6)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> browser.get("/api/v1/clinical/encounters").status()))
                .toList().stream().map(CompletableFuture::join).toList();

        assertThat(statuses).containsOnly(200);
        AUTH.verify(1, postRequestedFor(urlPathEqualTo("/oauth2/token")).withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=refresh-1")).withRequestBody(containing("client_assertion=ey")));
        SERVICES.verify(6, getRequestedFor(urlPathEqualTo("/api/v1/clinical/encounters")).withHeader("Authorization", equalTo("Bearer access-2")));
    }

    @Test
    void aRefreshTokenRejectedByAuthServiceEndsTheSession() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/", 5);
        AUTH.stubFor(post("/oauth2/token").withRequestBody(containing("grant_type=refresh_token"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody("{\"error\": \"invalid_grant\"}")));

        Browser.Response expired = browser.get("/api/v1/patients/123");

        assertThat(expired.status()).isEqualTo(401);
        assertThat(expired.json().get("code").asText()).isEqualTo("SESSION_EXPIRED");
        assertThat(browser.get("/bff/session").json().get("authenticated").asBoolean()).isFalse();
    }

    @Test
    void stepUpAsksForARecentSecondFactorAndNeverRedirectsOutsideTheFrontend() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/", 300);

        Browser.Response start = browser.get("/bff/step-up?returnTo=" + encode("http://evil.test/robar"));
        Browser.Response authorize = browser.get(start.location());
        Map<String, String> query = Browser.query(authorize.location());

        assertThat(authorize.location()).startsWith(ISSUER + "/oauth2/authorize");
        assertThat(query).containsEntry("max_age", "300").containsEntry("code_challenge_method", "S256");
        AUTH.stubFor(post("/oauth2/token").willReturn(okJson(tokenResponse(query.get("nonce"), "access-3", "refresh-3", 300))));
        assertThat(browser.get("/login/oauth2/code/clinica?code=step-up&state=" + encode(query.get("state"))).location()).isEqualTo(FRONTEND + "/");
    }

    @Test
    void logoutRevokesTheRefreshTokenAndReturnsTheAuthServiceEndSessionUrl() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/", 300);
        AUTH.stubFor(post("/oauth2/revoke").willReturn(aResponse().withStatus(200)));

        Browser.Response logout = browser.withCsrf("POST", "/bff/logout", null);

        assertThat(logout.status()).isEqualTo(200);
        assertThat(logout.json().get("endSessionUrl").asText()).startsWith(ISSUER + "/connect/logout?id_token_hint=ey")
                .contains("post_logout_redirect_uri=" + encode(FRONTEND + "/").replace("%2F", "/").replace("%3A", ":"));
        AUTH.verify(postRequestedFor(urlPathEqualTo("/oauth2/revoke")).withRequestBody(containing("token=refresh-1"))
                .withRequestBody(containing("client_assertion=ey")));
        assertThat(browser.get("/api/v1/patients/123").status()).isEqualTo(401);
    }

    @Test
    void theLoginApiOfAuthServiceKeepsItsOwnCookieAndSeesTheGatewayPrefix() {
        Browser browser = new Browser(port);
        browser.get("/bff/session");
        AUTH.stubFor(get("/api/v1/session").willReturn(okJson("{\"authenticated\": false}")));

        Browser.Response session = browser.get("/auth/api/v1/session", "Cookie", "CLINICA_AUTH_SESSION=abc", "Origin", FRONTEND,
                "X-Forwarded-Host", "evil.test", "X-Forwarded-Prefix", "/phishing");

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.header("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
        AUTH.verify(getRequestedFor(urlPathEqualTo("/api/v1/session"))
                .withHeader("X-Forwarded-Prefix", equalTo("/auth"))
                .withHeader("X-Forwarded-Host", equalTo("localhost:" + port))
                .withHeader("Cookie", containing("CLINICA_AUTH_SESSION=abc"))
                .withHeader("Cookie", notContaining("CLINICA_SESSION=")));
    }

    @Test
    void anUnreachableServiceAnswers503WithoutBreakingTheSession() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/", 300);
        SERVICES.stubFor(get("/api/v1/patients/999").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Browser.Response unavailable = browser.get("/api/v1/patients/999");

        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.json().get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(browser.get("/bff/session").json().get("authenticated").asBoolean()).isTrue();
    }

    @Test
    void relaysAdmissionsToItsServiceWithTheStaffTokenAndNoBrowserCredentials() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/admisiones", 300);
        SERVICES.stubFor(get("/api/v1/admissions/episodes/123").willReturn(okJson("{\"number\": \"ADM-2026-000001\"}")));

        Browser.Response episode = browser.get("/api/v1/admissions/episodes/123", "Origin", FRONTEND);

        assertThat(episode.status()).isEqualTo(200);
        assertThat(episode.body()).contains("ADM-2026-000001");
        SERVICES.verify(getRequestedFor(urlPathEqualTo("/api/v1/admissions/episodes/123"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withoutHeader("Cookie"));
    }

    @Test
    void letsAnybodyCheckAReceiptAndReadTheSealKeysWithoutASession() {
        Browser browser = new Browser(port);
        SERVICES.stubFor(post("/api/v1/admissions/receipts/verification").willReturn(okJson("{\"authentic\": true}")));
        SERVICES.stubFor(get("/api/v1/admissions/seal-keys").willReturn(okJson("{\"admissions-seal-v1\": \"pem\"}")));
        SERVICES.stubFor(get("/api/v1/clinical/seal-keys").willReturn(okJson("{\"clinical-seal-v1\": \"pem\"}")));

        Browser.Response checked = browser.send("POST", "/api/v1/admissions/receipts/verification",
                "{\"number\":\"ADM-2026-000001\",\"sha256\":\"" + "a".repeat(64) + "\"}",
                "Content-Type", "application/json");

        assertThat(checked.status()).isEqualTo(200);
        assertThat(checked.body()).contains("authentic");
        assertThat(browser.get("/api/v1/admissions/seal-keys").status()).isEqualTo(200);
        assertThat(browser.get("/api/v1/clinical/seal-keys").status()).isEqualTo(200);
        assertThat(browser.get("/api/v1/admissions/episodes/123").status()).isEqualTo(401);
        SERVICES.verify(postRequestedFor(urlPathEqualTo("/api/v1/admissions/receipts/verification"))
                .withoutHeader("Authorization")
                .withoutHeader("Cookie"));
    }

    @Test
    void relaysBillingToItsServiceWithTheStaffTokenAndNoBrowserCredentials() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/facturacion", 300);
        SERVICES.stubFor(get("/api/v1/billing/invoices/123").willReturn(okJson("{\"number\": \"SETP990000001\"}")));

        Browser.Response invoice = browser.get("/api/v1/billing/invoices/123", "Origin", FRONTEND);

        assertThat(invoice.status()).isEqualTo(200);
        assertThat(invoice.body()).contains("SETP990000001");
        SERVICES.verify(getRequestedFor(urlPathEqualTo("/api/v1/billing/invoices/123"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withoutHeader("Cookie"));
    }

    @Test
    void relaysTheInvoiceAssistantWithTheStaffTokenAndNoBrowserCredentials() {
        Browser browser = new Browser(port);
        signIn(browser, FRONTEND + "/facturacion", 300);
        SERVICES.stubFor(get("/api/v1/assistant/findings").willReturn(okJson("{\"content\": []}")));

        Browser.Response findings = browser.get("/api/v1/assistant/findings", "Origin", FRONTEND);

        assertThat(findings.status()).isEqualTo(200);
        assertThat(new Browser(port).get("/api/v1/assistant/findings").status()).isEqualTo(401);
        SERVICES.verify(getRequestedFor(urlPathEqualTo("/api/v1/assistant/findings"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withoutHeader("Cookie"));
    }

    @Test
    void letsAnybodyCheckAnInvoicePdfAndReadTheBillingSealKeysWithoutASession() {
        Browser browser = new Browser(port);
        SERVICES.stubFor(post("/api/v1/billing/graphic-representations/verification")
                .willReturn(okJson("{\"authentic\": true}")));
        SERVICES.stubFor(get("/api/v1/billing/seal-keys").willReturn(okJson("{\"billing-seal-v1\": \"pem\"}")));

        Browser.Response checked = browser.send("POST", "/api/v1/billing/graphic-representations/verification",
                "{\"number\":\"SETP990000001\",\"sha256\":\"" + "a".repeat(64) + "\"}",
                "Content-Type", "application/json");

        assertThat(checked.status()).isEqualTo(200);
        assertThat(checked.body()).contains("authentic");
        assertThat(browser.get("/api/v1/billing/seal-keys").status()).isEqualTo(200);
        assertThat(browser.get("/api/v1/billing/invoices/123").status()).isEqualTo(401);
        assertThat(browser.send("POST", "/api/v1/billing/invoices", "{}", "Content-Type", "application/json")
                .status()).isIn(401, 403);
        SERVICES.verify(postRequestedFor(urlPathEqualTo("/api/v1/billing/graphic-representations/verification"))
                .withoutHeader("Authorization")
                .withoutHeader("Cookie"));
    }

    @Test
    void corsOnlyTrustsTheFrontendOrigin() {
        Browser browser = new Browser(port);

        Browser.Response allowed = browser.send("OPTIONS", "/api/v1/patients", null, "Origin", FRONTEND, "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "x-xsrf-token,content-type");
        Browser.Response foreign = browser.send("OPTIONS", "/api/v1/patients", null, "Origin", "http://evil.test", "Access-Control-Request-Method", "POST");

        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.header("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
        assertThat(allowed.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(foreign.status()).isEqualTo(403);
    }

    private void signIn(Browser browser, String returnTo, long expiresIn) {
        Browser.Response start = browser.get("/bff/login?returnTo=" + encode(returnTo));
        assertThat(start.location()).isEqualTo("/oauth2/authorization/clinica");
        Browser.Response authorize = browser.get(start.location());
        Map<String, String> query = Browser.query(authorize.location());
        assertThat(authorize.location()).startsWith(ISSUER + "/oauth2/authorize");
        assertThat(query).containsEntry("client_id", "api-gateway").containsEntry("code_challenge_method", "S256").doesNotContainKey("max_age");
        AUTH.stubFor(post("/oauth2/token").withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(okJson(tokenResponse(query.get("nonce"), "access-1", "refresh-1", expiresIn))));
        Browser.Response callback = browser.get("/login/oauth2/code/clinica?code=code-1&state=" + encode(query.get("state")));
        assertThat(callback.location()).isEqualTo(returnTo);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
