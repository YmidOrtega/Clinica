package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import com.sun.net.httpserver.HttpServer;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySupportTest {

    private static final UUID NURSE = UUID.fromString("0b9e6a3c-1d2e-4f5a-8b7c-6d5e4f3a2b1c");

    private final CurrentUser currentUser = new CurrentUser();
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private HttpServer authServer;

    @BeforeEach
    void startAuthServer() throws IOException {
        authServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        authServer.createContext("/oauth2/token", exchange -> {
            Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tokenRequests.add(form);
            byte[] body = form.get("grant_type").equals("client_credentials")
                    ? "{\"access_token\":\"service-token\",\"expires_in\":1800}".getBytes(StandardCharsets.UTF_8)
                    : ("{\"access_token\":\"exchanged-for-" + form.get("audience") + "-" + tokenRequests.size() + "\",\"expires_in\":300}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(form.get("audience") != null && form.get("audience").equals("down") ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        authServer.start();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        authServer.stop(0);
    }

    @Test
    void outgoingCallsCarryATokenExchangedForTheTargetServiceNeverTheUserToken() {
        String userToken = authenticate(SecurityTestTokens.staff("NURSE", NURSE).value());
        DelegatedTokenInterceptor interceptor = new DelegatedTokenInterceptor(currentUser, delegatedTokens(), Map.of("patient-service", "patient-service"));

        RequestTemplate first = template("patient-service");
        interceptor.apply(first);
        RequestTemplate second = template("patient-service");
        interceptor.apply(second);
        RequestTemplate unknown = template("billing-service");
        interceptor.apply(unknown);

        assertThat(first.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer exchanged-for-patient-service-2");
        assertThat(second.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer exchanged-for-patient-service-2");
        assertThat(unknown.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
        assertThat(tokenRequests).hasSize(2);
        Map<String, String> exchange = tokenRequests.get(1);
        assertThat(exchange).containsEntry("grant_type", DelegatedTokens.TOKEN_EXCHANGE)
                .containsEntry("subject_token", userToken)
                .containsEntry("actor_token", "service-token")
                .containsEntry("client_id", "clinical-history-service")
                .containsEntry("client_assertion", "assertion-for-clinical-history-service@" + SecurityTestTokens.ISSUER);
    }

    @Test
    void servicesCallingOnTheirOwnDoNotGetAUserTokenAttached() {
        authenticate(SecurityTestTokens.service("patient-service").value());
        RequestTemplate template = template("patient-service");

        new DelegatedTokenInterceptor(currentUser, delegatedTokens(), Map.of("patient-service", "patient-service")).apply(template);

        assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
        assertThat(currentUser.service()).map(AuthenticatedService::clientId).contains("patient-service");
    }

    @Test
    void anUnreachableAuthServiceSurfacesAsADelegationFailure() {
        authenticate(SecurityTestTokens.staff("NURSE", NURSE).value());

        assertThatThrownBy(() -> delegatedTokens().forAudience("user-token", "down")).isInstanceOf(DelegationUnavailableException.class);
    }

    @Test
    void auditsWithTheStaffUuidAndNothingForServices() {
        CommonsSecurityAutoConfiguration.AuditingConfiguration auditing = new CommonsSecurityAutoConfiguration.AuditingConfiguration();
        assertThat(auditing.clinicaAuditorAware(currentUser).getCurrentAuditor()).isEmpty();

        authenticate(SecurityTestTokens.staff("RECEPTIONIST", NURSE).value());

        assertThat(auditing.clinicaAuditorAware(currentUser).getCurrentAuditor()).contains(NURSE.toString());
    }

    private DelegatedTokens delegatedTokens() {
        return new DelegatedTokens(RestClient.builder(), new ClinicaSecurityProperties.Client("clinical-history-service",
                "http://127.0.0.1:" + authServer.getAddress().getPort() + "/oauth2/token", "clinical-history-service-client", Map.of(),
                Duration.ofSeconds(60)), SecurityTestTokens.ISSUER, (clientId, audience) -> "assertion-for-" + clientId + "@" + audience,
                Clock.systemUTC());
    }

    private String authenticate(String token) {
        JwtDecoder decoder = ClinicaJwtDecoders.fromProperties(new ClinicaSecurityProperties.Jwt(null, SecurityTestTokens.jwkSet(),
                SecurityTestTokens.ISSUER, List.of(SecurityTestTokens.AUDIENCE), Duration.ofHours(1)), List.of());
        SecurityContextHolder.getContext().setAuthentication(new ClinicaJwtAuthenticationConverter().convert(decoder.decode(token)));
        return token;
    }

    private static RequestTemplate template(String feignClient) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, feignClient, "http://" + feignClient));
        return template;
    }

    private static Map<String, String> form(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
