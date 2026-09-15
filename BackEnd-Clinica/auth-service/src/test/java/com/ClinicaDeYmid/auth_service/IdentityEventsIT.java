package com.ClinicaDeYmid.auth_service;

import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.support.AuthTestSupport;
import com.ClinicaDeYmid.auth_service.support.BearerTokens;
import com.ClinicaDeYmid.auth_service.support.EventContracts;
import com.ClinicaDeYmid.auth_service.support.MySqlTestContainer;
import com.ClinicaDeYmid.auth_service.support.OAuthBrowser;
import com.ClinicaDeYmid.auth_service.support.StaffAccounts;
import com.ClinicaDeYmid.auth_service.support.StaffApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainer.class, AuthTestSupport.class, StaffAccounts.class})
class IdentityEventsIT {

    private static final String REASON = "Revisión de accesos del trimestre";

    @LocalServerPort
    private int port;

    @Autowired
    private StaffAccounts staff;

    @Autowired
    private BearerTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestSupport.register(registry);
    }

    @Test
    void administrativeChangesPublishTheFullUserStateAndWhoMadeThem() {
        User admin = staff.active(Role.ADMIN).user();
        StaffApi api = new StaffApi(port);
        String email = "evento." + UUID.randomUUID().toString().substring(0, 8) + "@clinica.test";
        JsonNode invited = api.post("/api/v1/users", tokens.fresh(admin), Map.of("email", email, "fullName", "Sara Méndez", "role", "NURSE")).json();
        String uuid = invited.get("uuid").asText();

        OAuthBrowser.Response deactivated = api.post("/api/v1/users/" + uuid + "/deactivation", tokens.fresh(admin), "\"0\"", Map.of("reason", REASON));
        assertThat(deactivated.status()).isEqualTo(200);

        List<JsonNode> userEvents = events("auth.users", uuid);
        assertThat(userEvents).extracting(event -> event.get("type").asText()).containsExactly("UserInvited", "UserDeactivated");
        JsonNode last = userEvents.getLast();
        assertThat(last.get("userVersion").asLong()).isEqualTo(1);
        assertThat(last.get("data").get("user").get("status").asText()).isEqualTo("DEACTIVATED");
        assertThat(last.get("data").get("user").get("email").asText()).isEqualTo(email);
        assertThat(last.get("data").get("user").get("tokensNotBefore").asText()).isNotBlank();

        List<JsonNode> audit = events("auth.security-audit", uuid);
        JsonNode deactivation = audit.stream().filter(event -> event.get("type").asText().equals("UserDeactivated")).findFirst().orElseThrow();
        assertThat(deactivation.get("actor").get("uuid").asText()).isEqualTo(admin.uuid().toString());
        assertThat(deactivation.get("details").get("reason").asText()).isEqualTo(REASON);
        assertThat(deactivation.get("client").get("address").asText()).isNotBlank();
        assertThat(deactivation.get("client").get("userAgent").asText()).isNotBlank();
    }

    @Test
    void signInsFailuresAndRefreshReuseAreAudited() {
        StaffAccounts.StaffAccount doctor = staff.active(Role.DOCTOR);
        String uuid = doctor.user().uuid().toString();
        OAuthBrowser browser = new OAuthBrowser(port);
        assertThat(browser.login(doctor.email(), "una contraseña que no es").status()).isEqualTo(401);
        browser.login(doctor.email(), StaffAccounts.PASSWORD);
        assertThat(browser.postJson("/api/v1/login/second-factor", Map.of("code", "000000"), true).status()).isEqualTo(401);
        browser.authorize();
        JsonNode issued = browser.exchangeCode(browser.authorizationCode(browser.signIn(doctor))).json();
        String firstRefresh = issued.get("refresh_token").asText();
        browser.refresh(firstRefresh);
        assertThat(browser.refresh(firstRefresh).status()).isEqualTo(400);
        String unknown = "nadie." + UUID.randomUUID().toString().substring(0, 8) + "@clinica.test";
        new OAuthBrowser(port).login(unknown, "cualquier frase larga");

        List<JsonNode> audit = events("auth.security-audit", uuid).stream()
                .filter(event -> !event.get("type").asText().startsWith("User"))
                .toList();
        assertThat(audit).extracting(event -> event.get("type").asText())
                .containsSubsequence("SignInFailed", "SignInFailed", "SignInCompleted", "RefreshTokenReuseDetected");
        assertThat(audit.get(0).get("details").get("stage").asText()).isEqualTo("PASSWORD");
        assertThat(audit.get(0).get("subject").get("email").asText()).isEqualTo(doctor.email());
        assertThat(audit.get(1).get("details").get("stage").asText()).isEqualTo("SECOND_FACTOR");
        JsonNode completed = audit.stream().filter(event -> event.get("type").asText().equals("SignInCompleted")).findFirst().orElseThrow();
        assertThat(completed.get("details").get("methods")).extracting(JsonNode::asText).containsExactly("pwd", "otp", "mfa");
        assertThat(completed.get("details").get("stepUp").asBoolean()).isFalse();

        String unknownFailure = jdbc.queryForObject("""
                SELECT payload FROM auth_outbox.outbox_events
                WHERE type = 'SignInFailed' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.subject.email')) = ?""", String.class, unknown);
        assertThat(EventContracts.securityAuditViolations(unknownFailure)).isEmpty();
        assertThat(EventContracts.parse(unknownFailure).get("subject").has("uuid")).isFalse();
    }

    @Test
    void everyStoredEventMatchesItsPublishedContract() {
        staff.active(Role.RECEPTIONIST);

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT aggregatetype, type, payload FROM auth_outbox.outbox_events");

        assertThat(rows).isNotEmpty().allSatisfy(row -> {
            String payload = (String) row.get("payload");
            List<String> violations = row.get("aggregatetype").equals("auth.users")
                    ? EventContracts.userEventViolations(payload)
                    : EventContracts.securityAuditViolations(payload);
            assertThat(violations).as("%s %s", row.get("type"), payload).isEmpty();
            assertThat(EventContracts.parse(payload).get("type").asText()).isEqualTo(row.get("type"));
        });
    }

    private List<JsonNode> events(String aggregateType, String aggregateId) {
        return jdbc.queryForList("""
                SELECT payload FROM auth_outbox.outbox_events WHERE aggregatetype = ? AND aggregateid = ? ORDER BY created_at, type""",
                String.class, aggregateType, aggregateId).stream().map(EventContracts::parse).toList();
    }
}
